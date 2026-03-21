terraform {
  required_version = ">= 1.7"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.40"
    }
    aiven = {
      source  = "aiven/aiven"
      version = "~> 4.20"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "aws" {
  region = var.aws_region
  default_tags {
    tags = {
      Project   = "QwikBrew"
      ManagedBy = "Terraform"
    }
  }
}

provider "aiven" {
  api_token = var.aiven_api_token
}

# ═══════════════════════════════════════════════════════════════════════════════
# VPC
# Free tier: VPC itself is free. NAT Gateway is NOT free (~$32/month).
# We use a single NAT Gateway (cheapest option — one AZ only).
# ═══════════════════════════════════════════════════════════════════════════════
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "5.7.0"

  name = "qwikbrew-vpc"
  cidr = "10.0.0.0/16"

  azs             = ["${var.aws_region}a", "${var.aws_region}b"]
  private_subnets = ["10.0.1.0/24", "10.0.2.0/24"]
  public_subnets  = ["10.0.101.0/24", "10.0.102.0/24"]

  # Single NAT gateway — covers both AZs from one gateway to minimize cost
  enable_nat_gateway   = true
  single_nat_gateway   = true
  enable_dns_hostnames = true
  enable_dns_support   = true

  public_subnet_tags = {
    "kubernetes.io/role/elb" = "1"
  }
  private_subnet_tags = {
    "kubernetes.io/role/internal-elb"  = "1"
    "kubernetes.io/cluster/qwikbrew"   = "owned"
  }
}

# ═══════════════════════════════════════════════════════════════════════════════
# EKS
# Note: EKS control plane costs $0.10/hour (~$72/month) — not free tier.
# This is the minimum viable config for running all 6 microservices.
#
# Node: t3.medium (2 vCPU, 4 GB RAM)
#   - t2.micro/t3.micro (1 GB) — too small, EKS system pods alone use ~1.2 GB
#   - t3.small (2 GB) — nodes start but go NotReady under full pod load
#   - t3.medium (4 GB) — minimum that works reliably for EKS
#
# Cost saver: single node group, minimum 2 nodes (EKS needs 2 for HA addons)
# ═══════════════════════════════════════════════════════════════════════════════
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "20.8.0"

  cluster_name    = "qwikbrew"
  cluster_version = "1.29"

  vpc_id                                   = module.vpc.vpc_id
  subnet_ids                               = module.vpc.private_subnets
  cluster_endpoint_public_access           = true
  enable_irsa                              = true
  enable_cluster_creator_admin_permissions = true

  eks_managed_node_groups = {
    nodes = {
      # Only valid EKS Spot instance types with 4 GB+ RAM
      instance_types = ["t3.medium", "t3.large"]
      capacity_type  = "SPOT"

      min_size     = 2
      max_size     = 3
      desired_size = 2

      # Attach all three required managed policies to the node IAM role.
      # Without AmazonEKS_CNI_Policy, vpc-cni cannot assign pod IPs
      # and nodes stay in NotReady state even after launching successfully.
      iam_role_additional_policies = {
        AmazonEKSWorkerNodePolicy          = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
        AmazonEKS_CNI_Policy               = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
        AmazonEC2ContainerRegistryReadOnly = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
        AmazonSSMManagedInstanceCore       = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
      }

      block_device_mappings = {
        xvda = {
          device_name = "/dev/xvda"
          ebs = {
            volume_size           = 20
            volume_type           = "gp2"
            delete_on_termination = true
          }
        }
      }
    }
  }

  # vpc-cni before coredns — nodes need IP assignment before DNS can start
  cluster_addons = {
    vpc-cni = {
      most_recent              = true
      before_compute           = true
      configuration_values     = jsonencode({
        env = {
          ENABLE_PREFIX_DELEGATION = "true"
          WARM_PREFIX_TARGET       = "1"
        }
      })
    }
    coredns = {
      most_recent = true
    }
    kube-proxy = {
      most_recent = true
    }
  }
}


# ═══════════════════════════════════════════════════════════════════════════════
# ELB PERMISSIONS — required for type:LoadBalancer services to get EXTERNAL-IP
# Without this, the cloud controller cannot create Classic Load Balancers
# ═══════════════════════════════════════════════════════════════════════════════
resource "aws_iam_role_policy" "node_elb_policy" {
  name = "qwikbrew-node-elb-policy"
  role = module.eks.eks_managed_node_groups["nodes"].iam_role_name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "elasticloadbalancing:*",
          "ec2:Describe*",
          "ec2:AuthorizeSecurityGroupIngress",
          "ec2:RevokeSecurityGroupIngress",
          "ec2:CreateSecurityGroup",
          "ec2:DeleteSecurityGroup"
        ]
        Resource = "*"
      }
    ]
  })
}

# ═══════════════════════════════════════════════════════════════════════════════
# ECR — one private repo per microservice
# Free tier: 500 MB storage free per month
# ═══════════════════════════════════════════════════════════════════════════════
locals {
  services = [
    "api-gateway",
    "user-service",
    "menu-service",
    "order-service",
    "payment-service",
    "notification-service",
  ]
}

resource "aws_ecr_repository" "services" {
  for_each             = toset(local.services)
  name                 = "qwikbrew/${each.key}"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "cleanup" {
  for_each   = aws_ecr_repository.services
  repository = each.value.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep last 5 images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 5
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

# ═══════════════════════════════════════════════════════════════════════════════
# AIVEN POSTGRESQL
# Use "startup-4" plan — cheapest paid plan (~$19/month).
# One service, five logical databases (one per microservice).
# ═══════════════════════════════════════════════════════════════════════════════
resource "aiven_pg" "qwikbrew" {
  project      = var.aiven_project
  cloud_name   = var.aiven_cloud_region
  plan         = "startup-4"
  service_name = "qwikbrew-pg"

  maintenance_window_dow  = "sunday"
  maintenance_window_time = "02:00:00"

  pg_user_config {
    pg_version    = "16"
    backup_hour   = 3
    backup_minute = 0

    pg {
      idle_in_transaction_session_timeout = 900
      log_min_duration_statement          = 1000
    }

    pgbouncer {
      autodb_pool_mode = "transaction"
    }

    ip_filter_object {
      network = "0.0.0.0/0"
    }
  }

  timeouts {
    create = "30m"
    update = "15m"
    delete = "15m"
  }
}

locals {
  databases = ["userdb", "menudb", "orderdb", "paymentdb", "notificationdb"]
}

resource "aiven_pg_database" "dbs" {
  for_each      = toset(local.databases)
  project       = var.aiven_project
  service_name  = aiven_pg.qwikbrew.service_name
  database_name = each.key
}

# ═══════════════════════════════════════════════════════════════════════════════
# AIVEN KAFKA
# Use "startup-2" plan — cheapest Kafka plan (~$35/month).
# kafka_version is omitted — Aiven uses its current default automatically.
# ═══════════════════════════════════════════════════════════════════════════════
resource "aiven_kafka" "qwikbrew" {
  project      = var.aiven_project
  cloud_name   = var.aiven_cloud_region
  # startup-4: 3-broker HA cluster — minimum plan for Kafka 4.x
  # startup-2 is deprecated for Kafka 3.9 and above
  plan         = "startup-4"
  service_name = "qwikbrew-kafka"

  maintenance_window_dow  = "sunday"
  maintenance_window_time = "03:00:00"

  kafka_user_config {
    # Kafka 4.0 — latest confirmed on Aiven.
    # Update to "4.1" once Aiven makes it available in your region.
    kafka_version   = "4.0"
    kafka_rest      = true
    schema_registry = true   # enabled — startup-4 has enough memory

    kafka {
      auto_create_topics_enable  = false
      num_partitions             = 3
      # startup-4 has 3 brokers — replication_factor up to 3 is valid
      default_replication_factor = 2
      min_insync_replicas        = 1
      log_retention_hours        = 168
    }

    ip_filter_object {
      network = "0.0.0.0/0"
    }
  }

  timeouts {
    create = "30m"
    update = "15m"
    delete = "15m"
  }
}

# Six Kafka topics
locals {
  kafka_topics = {
    "order-placed"    = { partitions = 3, retention_ms = "604800000" }
    "order-ready"     = { partitions = 3, retention_ms = "604800000" }
    "order-cancelled" = { partitions = 3, retention_ms = "604800000" }
    "wallet-topup"    = { partitions = 3, retention_ms = "604800000" }
    "points-earned"   = { partitions = 3, retention_ms = "604800000" }
    "notifications"   = { partitions = 3, retention_ms = "86400000"  }
  }
}

resource "aiven_kafka_topic" "topics" {
  for_each     = local.kafka_topics
  project      = var.aiven_project
  service_name = aiven_kafka.qwikbrew.service_name
  topic_name   = each.key
  partitions   = each.value.partitions
  replication  = 2   # startup-4 has 3 brokers — replication 2 is safe

  config {
    retention_ms   = each.value.retention_ms
    cleanup_policy = "delete"
  }
}

resource "random_password" "kafka_password" {
  length  = 32
  special = false
}

resource "aiven_kafka_user" "app" {
  project      = var.aiven_project
  service_name = aiven_kafka.qwikbrew.service_name
  username     = "qwikbrew-app"
  password     = random_password.kafka_password.result
}

resource "aiven_kafka_acl" "produce" {
  project      = var.aiven_project
  service_name = aiven_kafka.qwikbrew.service_name
  username     = aiven_kafka_user.app.username
  permission   = "write"
  topic        = "*"
}

resource "aiven_kafka_acl" "consume" {
  project      = var.aiven_project
  service_name = aiven_kafka.qwikbrew.service_name
  username     = aiven_kafka_user.app.username
  permission   = "read"
  topic        = "*"
}

# ═══════════════════════════════════════════════════════════════════════════════
# AWS SECRETS MANAGER
# Stores all connection strings. Pods read these via External Secrets Operator.
# Free tier: first 10,000 API calls/month free.
# ═══════════════════════════════════════════════════════════════════════════════
resource "random_password" "jwt_secret" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "aiven_credentials" {
  name                    = "qwikbrew/staging/aiven-credentials"
  recovery_window_in_days = 0   # instant delete on destroy (no retention cost)
  description             = "Aiven PG and Kafka credentials for QwikBrew"
}

resource "aws_secretsmanager_secret_version" "aiven_credentials" {
  secret_id = aws_secretsmanager_secret.aiven_credentials.id

  secret_string = jsonencode({
    PG_USER     = aiven_pg.qwikbrew.service_username
    PG_PASSWORD = aiven_pg.qwikbrew.service_password
    PG_HOST     = aiven_pg.qwikbrew.service_host
    PG_PORT     = tostring(aiven_pg.qwikbrew.service_port)

    USERDB_URL         = "jdbc:postgresql://${aiven_pg.qwikbrew.service_host}:${aiven_pg.qwikbrew.service_port}/userdb?sslmode=require"
    MENUDB_URL         = "jdbc:postgresql://${aiven_pg.qwikbrew.service_host}:${aiven_pg.qwikbrew.service_port}/menudb?sslmode=require"
    ORDERDB_URL        = "jdbc:postgresql://${aiven_pg.qwikbrew.service_host}:${aiven_pg.qwikbrew.service_port}/orderdb?sslmode=require"
    PAYMENTDB_URL      = "jdbc:postgresql://${aiven_pg.qwikbrew.service_host}:${aiven_pg.qwikbrew.service_port}/paymentdb?sslmode=require"
    NOTIFICATIONDB_URL = "jdbc:postgresql://${aiven_pg.qwikbrew.service_host}:${aiven_pg.qwikbrew.service_port}/notificationdb?sslmode=require"

    KAFKA_BOOTSTRAP_SERVERS = "${aiven_kafka.qwikbrew.service_host}:${aiven_kafka.qwikbrew.service_port}"
    KAFKA_SECURITY_PROTOCOL = "SASL_SSL"
    KAFKA_SASL_MECHANISM    = "PLAIN"
    KAFKA_SASL_USERNAME     = aiven_kafka_user.app.username
    KAFKA_SASL_PASSWORD     = aiven_kafka_user.app.password
  })

  depends_on = [
    aiven_pg.qwikbrew,
    aiven_kafka.qwikbrew,
    aiven_kafka_user.app,
  ]
}

resource "aws_secretsmanager_secret" "app_secrets" {
  name                    = "qwikbrew/staging/app-secrets"
  recovery_window_in_days = 0
  description             = "App secrets for QwikBrew"
}

resource "aws_secretsmanager_secret_version" "app_secrets" {
  secret_id = aws_secretsmanager_secret.app_secrets.id

  secret_string = jsonencode({
    JWT_SECRET             = random_password.jwt_secret.result
    # No Redis auth token — ElastiCache removed for free tier
    PAYMENT_GATEWAY_KEY_ID = var.payment_gateway_key_id
    PAYMENT_GATEWAY_SECRET = var.payment_gateway_secret
    FCM_SERVER_KEY         = var.fcm_server_key
    SMTP_USER              = var.smtp_user
    SMTP_PASS              = var.smtp_pass
  })
}

# ═══════════════════════════════════════════════════════════════════════════════
# OUTPUTS
# ═══════════════════════════════════════════════════════════════════════════════
output "cluster_name" {
  value = module.eks.cluster_name
}

output "cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "pg_host" {
  value = aiven_pg.qwikbrew.service_host
}

output "pg_port" {
  value = aiven_pg.qwikbrew.service_port
}

output "kafka_bootstrap" {
  value = "${aiven_kafka.qwikbrew.service_host}:${aiven_kafka.qwikbrew.service_port}"
}

output "ecr_urls" {
  value = {
    for k, v in aws_ecr_repository.services : k => v.repository_url
  }
}

output "aiven_secrets_arn" {
  value = aws_secretsmanager_secret.aiven_credentials.arn
}

# redis_endpoint output removed — ElastiCache not used on free tier
# Menu service uses Spring's in-memory cache instead (no external dependency)
