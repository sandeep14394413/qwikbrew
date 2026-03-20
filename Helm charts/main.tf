terraform {
  required_version = ">= 1.7"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.40" }
    kubernetes = { source = "hashicorp/kubernetes", version = "~> 2.27" }
    helm       = { source = "hashicorp/helm",       version = "~> 2.13" }
  }

  backend "s3" {
    bucket         = "qwikbrew-terraform-state"
    key            = "eks/terraform.tfstate"
    region         = "ap-south-1"
    encrypt        = true
    dynamodb_table = "qwikbrew-tf-lock"
  }
}

provider "aws" {
  region = var.aws_region
  default_tags { tags = local.common_tags }
}

locals {
  cluster_name = "qwikbrew-${var.environment}"
  common_tags  = {
    Project     = "QwikBrew"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# ── VPC ─────────────────────────────────────────────────────────────────────
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "5.7.0"

  name = "${local.cluster_name}-vpc"
  cidr = "10.0.0.0/16"

  azs             = ["${var.aws_region}a", "${var.aws_region}b", "${var.aws_region}c"]
  private_subnets = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
  public_subnets  = ["10.0.101.0/24", "10.0.102.0/24", "10.0.103.0/24"]

  enable_nat_gateway     = true
  single_nat_gateway     = var.environment == "prod" ? false : true
  enable_dns_hostnames   = true
  enable_dns_support     = true

  public_subnet_tags  = { "kubernetes.io/role/elb" = "1" }
  private_subnet_tags = { "kubernetes.io/role/internal-elb" = "1",
                          "kubernetes.io/cluster/${local.cluster_name}" = "owned" }
}

# ── EKS Cluster ─────────────────────────────────────────────────────────────
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "20.8.0"

  cluster_name    = local.cluster_name
  cluster_version = "1.29"

  vpc_id                         = module.vpc.vpc_id
  subnet_ids                     = module.vpc.private_subnets
  cluster_endpoint_public_access = true

  # Managed node groups
  eks_managed_node_groups = {
    system = {
      name           = "system"
      instance_types = ["t3.medium"]
      min_size       = 2
      max_size       = 4
      desired_size   = 2
      labels = { role = "system" }
      taints = []
    }

    app = {
      name           = "app"
      instance_types = var.environment == "prod" ? ["t3.large"] : ["t3.medium"]
      min_size       = var.environment == "prod" ? 3 : 2
      max_size       = var.environment == "prod" ? 10 : 4
      desired_size   = var.environment == "prod" ? 3 : 2
      labels = { role = "app" }

      block_device_mappings = {
        xvda = {
          device_name = "/dev/xvda"
          ebs = { volume_size = 50, volume_type = "gp3", delete_on_termination = true }
        }
      }
    }
  }

  # Enable IRSA
  enable_irsa = true

  cluster_addons = {
    coredns    = { most_recent = true }
    kube-proxy = { most_recent = true }
    vpc-cni    = { most_recent = true }
    aws-ebs-csi-driver = { most_recent = true }
  }
}

# ── RDS Aurora PostgreSQL (per-service databases) ────────────────────────────
module "rds" {
  source  = "terraform-aws-modules/rds-aurora/aws"
  version = "9.3.1"

  name           = "${local.cluster_name}-postgres"
  engine         = "aurora-postgresql"
  engine_version = "15.4"
  instance_class = var.environment == "prod" ? "db.r6g.large" : "db.t4g.medium"

  instances = {
    writer = {}
    reader = var.environment == "prod" ? {} : null
  }

  vpc_id               = module.vpc.vpc_id
  db_subnet_group_name = aws_db_subnet_group.main.name
  security_group_ids   = [aws_security_group.rds.id]

  master_username             = "qwikbrew"
  manage_master_user_password = true   # stored in Secrets Manager

  storage_encrypted   = true
  deletion_protection = var.environment == "prod"
  skip_final_snapshot = var.environment != "prod"
  backup_retention_period = var.environment == "prod" ? 7 : 1

  monitoring_interval = 60
  enabled_cloudwatch_logs_exports = ["postgresql"]
}

resource "aws_db_subnet_group" "main" {
  name       = "${local.cluster_name}-db-subnet"
  subnet_ids = module.vpc.private_subnets
}

# ── ElastiCache Redis ────────────────────────────────────────────────────────
resource "aws_elasticache_replication_group" "redis" {
  replication_group_id       = "${local.cluster_name}-redis"
  description                = "QwikBrew Redis cache"
  node_type                  = var.environment == "prod" ? "cache.r6g.large" : "cache.t4g.micro"
  num_cache_clusters         = var.environment == "prod" ? 2 : 1
  automatic_failover_enabled = var.environment == "prod"
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  auth_token                 = random_password.redis_auth.result
  subnet_group_name          = aws_elasticache_subnet_group.redis.name
  security_group_ids         = [aws_security_group.redis.id]

  log_delivery_configuration {
    destination      = aws_cloudwatch_log_group.redis.name
    destination_type = "cloudwatch-logs"
    log_format       = "text"
    log_type         = "slow-log"
  }
}

resource "aws_elasticache_subnet_group" "redis" {
  name       = "${local.cluster_name}-redis-subnet"
  subnet_ids = module.vpc.private_subnets
}

resource "random_password" "redis_auth" {
  length  = 32
  special = false
}

# ── Amazon MSK (Kafka) ───────────────────────────────────────────────────────
resource "aws_msk_cluster" "kafka" {
  cluster_name           = "${local.cluster_name}-kafka"
  kafka_version          = "3.6.0"
  number_of_broker_nodes = var.environment == "prod" ? 3 : 2

  broker_node_group_info {
    instance_type   = var.environment == "prod" ? "kafka.m5.large" : "kafka.t3.small"
    client_subnets  = slice(module.vpc.private_subnets, 0, var.environment == "prod" ? 3 : 2)
    storage_info {
      ebs_storage_info { volume_size = var.environment == "prod" ? 100 : 20 }
    }
    security_groups = [aws_security_group.msk.id]
  }

  encryption_info {
    encryption_in_transit { client_broker = "TLS", in_cluster = true }
  }

  logging {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.msk.name
      }
    }
  }
}

# ── ECR Repositories ─────────────────────────────────────────────────────────
locals {
  services = ["api-gateway", "user-service", "menu-service", "order-service", "payment-service", "notification-service"]
}

resource "aws_ecr_repository" "services" {
  for_each = toset(local.services)
  name                 = "qwikbrew/${each.key}"
  image_tag_mutability = "MUTABLE"
  image_scanning_configuration { scan_on_push = true }
  encryption_configuration      { encryption_type = "AES256" }
}

resource "aws_ecr_lifecycle_policy" "cleanup" {
  for_each   = aws_ecr_repository.services
  repository = each.value.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 10 images"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 10 }
      action       = { type = "expire" }
    }]
  })
}

# ── Secrets Manager ──────────────────────────────────────────────────────────
resource "aws_secretsmanager_secret" "app_secrets" {
  name = "qwikbrew/${var.environment}/app-secrets"
  recovery_window_in_days = var.environment == "prod" ? 30 : 0
}

resource "aws_secretsmanager_secret_version" "app_secrets" {
  secret_id = aws_secretsmanager_secret.app_secrets.id
  secret_string = jsonencode({
    JWT_SECRET               = random_password.jwt_secret.result
    REDIS_AUTH_TOKEN         = random_password.redis_auth.result
    PAYMENT_GATEWAY_KEY_ID   = var.payment_gateway_key_id
    PAYMENT_GATEWAY_SECRET   = var.payment_gateway_secret
    FCM_SERVER_KEY           = var.fcm_server_key
    SMTP_HOST                = var.smtp_host
    SMTP_USER                = var.smtp_user
    SMTP_PASS                = var.smtp_pass
  })
}

resource "random_password" "jwt_secret" {
  length  = 64
  special = false
}

# ── Route53 + ACM ────────────────────────────────────────────────────────────
resource "aws_route53_zone" "main" {
  count = var.domain_name != "" ? 1 : 0
  name  = var.domain_name
}

resource "aws_acm_certificate" "api" {
  count             = var.domain_name != "" ? 1 : 0
  domain_name       = "api.${var.domain_name}"
  validation_method = "DNS"
  lifecycle { create_before_destroy = true }
}

# ── Security Groups ──────────────────────────────────────────────────────────
resource "aws_security_group" "rds" {
  name   = "${local.cluster_name}-rds-sg"
  vpc_id = module.vpc.vpc_id
  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = module.vpc.private_subnets_cidr_blocks
  }
  egress { from_port = 0; to_port = 0; protocol = "-1"; cidr_blocks = ["0.0.0.0/0"] }
}

resource "aws_security_group" "redis" {
  name   = "${local.cluster_name}-redis-sg"
  vpc_id = module.vpc.vpc_id
  ingress {
    from_port   = 6379
    to_port     = 6379
    protocol    = "tcp"
    cidr_blocks = module.vpc.private_subnets_cidr_blocks
  }
  egress { from_port = 0; to_port = 0; protocol = "-1"; cidr_blocks = ["0.0.0.0/0"] }
}

resource "aws_security_group" "msk" {
  name   = "${local.cluster_name}-msk-sg"
  vpc_id = module.vpc.vpc_id
  ingress {
    from_port   = 9092
    to_port     = 9094
    protocol    = "tcp"
    cidr_blocks = module.vpc.private_subnets_cidr_blocks
  }
  egress { from_port = 0; to_port = 0; protocol = "-1"; cidr_blocks = ["0.0.0.0/0"] }
}

# ── CloudWatch Log Groups ────────────────────────────────────────────────────
resource "aws_cloudwatch_log_group" "redis" {
  name              = "/qwikbrew/${var.environment}/redis"
  retention_in_days = 7
}

resource "aws_cloudwatch_log_group" "msk" {
  name              = "/qwikbrew/${var.environment}/msk"
  retention_in_days = 7
}

# ── Outputs ──────────────────────────────────────────────────────────────────
output "cluster_name"        { value = module.eks.cluster_name }
output "cluster_endpoint"    { value = module.eks.cluster_endpoint }
output "ecr_urls" {
  value = { for k, v in aws_ecr_repository.services : k => v.repository_url }
}
output "rds_endpoint"        { value = module.rds.cluster_endpoint }
output "redis_endpoint"      { value = aws_elasticache_replication_group.redis.primary_endpoint_address }
output "msk_bootstrap_brokers" { value = aws_msk_cluster.kafka.bootstrap_brokers_tls }
