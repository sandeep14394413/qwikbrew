# 🚀 QwikBrew — AWS EKS Deployment Runbook

## Prerequisites
```bash
# Install tools
brew install awscli terraform kubectl helm
aws configure   # set AWS_ACCESS_KEY_ID, SECRET, region=ap-south-1

# Verify
aws sts get-caller-identity
```

---

## Step 1 — Bootstrap Terraform State Backend
```bash
# Run ONCE to create the S3 bucket + DynamoDB lock table
aws s3api create-bucket \
  --bucket qwikbrew-terraform-state \
  --region ap-south-1 \
  --create-bucket-configuration LocationConstraint=ap-south-1

aws s3api put-bucket-versioning \
  --bucket qwikbrew-terraform-state \
  --versioning-configuration Status=Enabled

aws dynamodb create-table \
  --table-name qwikbrew-tf-lock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region ap-south-1
```

---

## Step 2 — Provision AWS Infrastructure (Terraform)
```bash
cd terraform/

# Fill in your secrets
cp terraform.tfvars.example terraform.tfvars
# Edit: payment_gateway_key_id, fcm_server_key, smtp credentials, domain_name

terraform init
terraform plan -out=tfplan
terraform apply tfplan

# Save the outputs — you'll need them for k8s
terraform output -json > ../tf-outputs.json
```

**What gets created:**
| Resource         | Detail                                           |
|------------------|--------------------------------------------------|
| EKS Cluster      | v1.29, 2–10 nodes (t3.medium/large)              |
| Aurora PostgreSQL | 5 databases (one per service)                   |
| ElastiCache Redis | 1–2 node Redis 7                                |
| Amazon MSK       | 2–3 broker Kafka 3.6                             |
| ECR              | 6 private repos with lifecycle policies          |
| ACM + Route53    | TLS cert + DNS (if domain_name set)              |
| Secrets Manager  | All app secrets stored encrypted                 |

---

## Step 3 — Install Cluster Add-ons (Helm)
```bash
# Point kubectl at your new cluster
aws eks update-kubeconfig --name qwikbrew-staging --region ap-south-1

# AWS Load Balancer Controller
helm repo add eks https://aws.github.io/eks-charts && helm repo update
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=qwikbrew-staging \
  --set serviceAccount.create=true

# External Secrets Operator (pulls secrets from AWS Secrets Manager)
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets external-secrets/external-secrets \
  -n external-secrets --create-namespace

# Prometheus + Grafana stack
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace \
  --set grafana.adminPassword=qwikbrew_admin \
  --set grafana.service.type=LoadBalancer

# Cluster Autoscaler
helm install cluster-autoscaler autoscaler/cluster-autoscaler \
  -n kube-system \
  --set autoDiscovery.clusterName=qwikbrew-staging \
  --set awsRegion=ap-south-1
```

---

## Step 4 — Create Databases in RDS
```bash
# Get RDS endpoint from Terraform output
RDS=$(cat tf-outputs.json | jq -r '.rds_endpoint.value')

# Connect and create per-service databases
psql "postgresql://qwikbrew:<password>@$RDS:5432/postgres" <<SQL
CREATE DATABASE userdb;
CREATE DATABASE menudb;
CREATE DATABASE orderdb;
CREATE DATABASE paymentdb;
CREATE DATABASE notificationdb;
SQL
```

---

## Step 5 — Build & Push Docker Images to ECR
```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGISTRY="$ACCOUNT_ID.dkr.ecr.ap-south-1.amazonaws.com"

aws ecr get-login-password | docker login --username AWS --password-stdin $REGISTRY

for SERVICE in api-gateway user-service menu-service order-service payment-service notification-service; do
  # Build JAR first
  mvn -pl $SERVICE package -DskipTests -q

  docker build --build-arg SERVICE=$SERVICE \
    -t $REGISTRY/qwikbrew/$SERVICE:latest \
    -t $REGISTRY/qwikbrew/$SERVICE:$(git rev-parse --short HEAD) .

  docker push $REGISTRY/qwikbrew/$SERVICE --all-tags
  echo "✅ Pushed $SERVICE"
done
```

---

## Step 6 — Deploy to EKS
```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
RDS=$(cat tf-outputs.json | jq -r '.rds_endpoint.value')
REDIS=$(cat tf-outputs.json | jq -r '.redis_endpoint.value')
MSK=$(cat tf-outputs.json | jq -r '.msk_bootstrap_brokers.value')

# Substitute real values into manifests
find k8s/ -name '*.yaml' -exec sed -i \
  -e "s|ACCOUNT_ID|$ACCOUNT_ID|g" \
  -e "s|RDS_ENDPOINT|$RDS|g" \
  -e "s|REDIS_ENDPOINT|$REDIS|g" \
  -e "s|MSK_BOOTSTRAP_BROKERS|$MSK|g" \
  {} \;

# Apply everything
kubectl apply -f k8s/

# Watch rollout
kubectl get pods -n qwikbrew -w
```

---

## Step 7 — Set GitHub Actions Secrets
In your GitHub repo → **Settings → Secrets and Variables → Actions**, add:

| Secret                   | Value                                  |
|--------------------------|----------------------------------------|
| `AWS_ACCOUNT_ID`         | Your 12-digit AWS account ID           |
| `RDS_ENDPOINT`           | From `terraform output rds_endpoint`   |
| `REDIS_ENDPOINT`         | From `terraform output redis_endpoint` |
| `MSK_BOOTSTRAP_BROKERS`  | From `terraform output msk_bootstrap_brokers` |
| `SLACK_WEBHOOK_URL`      | Your Slack incoming webhook URL        |

Then create a GitHub OIDC IAM role:
```bash
# Trust policy for GitHub OIDC
aws iam create-role --role-name qwikbrew-github-actions \
  --assume-role-policy-document file://infra/github-oidc-trust.json

aws iam attach-role-policy --role-name qwikbrew-github-actions \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryPowerUser

aws iam attach-role-policy --role-name qwikbrew-github-actions \
  --policy-arn arn:aws:iam::aws:policy/AmazonEKSClusterPolicy
```

After this, every `git push` to `main` → auto builds, scans (Trivy), and deploys to EKS.

---

## Useful Commands
```bash
# View all pods
kubectl get pods -n qwikbrew

# Tail logs of a service
kubectl logs -f -l app=order-service -n qwikbrew

# Port-forward Grafana
kubectl port-forward svc/kube-prometheus-stack-grafana 3000:80 -n monitoring

# Force restart a deployment
kubectl rollout restart deployment/user-service -n qwikbrew

# Scale manually
kubectl scale deployment/api-gateway --replicas=4 -n qwikbrew

# Get Load Balancer URL
kubectl get ingress qwikbrew-ingress -n qwikbrew
```

---

## Architecture Summary

```
Internet → Route53 → ALB (HTTPS) → api-gateway pod
                                         │
               ┌───────────┬─────────────┼─────────────┬───────────────┐
          user-svc    menu-svc      order-svc     payment-svc   notification-svc
               │            │             │              │               │
          Aurora RDS  Aurora+Redis   Aurora RDS    Aurora RDS    Amazon MSK ←┘
          (userdb)    (menudb)       (orderdb)     (paymentdb)   (Kafka topics)
```

**Cost estimate (staging):** ~$250–350/month
**Cost estimate (prod):**    ~$700–1,200/month
