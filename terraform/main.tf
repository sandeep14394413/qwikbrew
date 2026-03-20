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
      Project     = "QwikBrew"
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}

provider "aiven" {
  api_token = var.aiven_api_token
}

locals {
  env        = var.environment
  is_prod    = local.env == "prod"
  pg_plan    = local.is_prod ? "business-4" : "startup-4"
  kafka_plan = local.is_prod ? "business-4" : "startup-2"
}

# ═══════════════════════════════════════════════════════════════════════════════
# VPC
# ═══════════════════════════════════════════════════════════════════════════════
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "5.7.0"

  name = "qwikbrew-${local.env}-vpc"
  cidr = "10.0.0.0/16"

  azs             = ["${var.aws_region}a", "${var.aws_region}b", "${var.aws_region}c"]
  private_subnets = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
  public_subnets  = ["10.0.101.0/24", "10.0.102.0/24", "10.0.103.0/24"]

  enable_nat_gateway   = true
  single_nat_gateway   = !local.is_prod
  enable_dns_hostnames = true
  enable_dns_support   = true

  public_subnet_tags  = { "kubernetes.io/role/elb" = "1" }
  private_subnet_tags = {
    "kubernetes.io/role/internal-elb"                           = "1"
    "kubernetes.io/cluster/qwikbrew-${local.env}"               = "owned"
  }
}

# ═══════════════════════════════════════════════════════════════════════════════
# EKS
# ═══════════════════════════════════════════════════════════════════════════════
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "20.8.0"

  cluster_name    = "qwikbrew-${local.env}"
  cluster_version = "1.29"

  vpc_id                         = module.vpc.vpc_id
  subnet_ids                     = module.vpc.private_subnets
  cluster_endpoint_public_access = true
  enable_irsa                    = true

  eks_managed_node_groups = {
    system = {
      instance_types = ["t3.medium"]
      min_size       = 2
      max_size       = 4
      desired_size   = 2
      labels         = { role = "system" }
    }
    app = {
      instance_types = [local.is_prod ? "t3.large" : "t3.medium"]
      min_size       = local.is_prod ? 3 : 2
      max_size       = local.is_prod ? 10 : 4
      desired_size   = local.is_prod ? 3 : 2
      labels         = { role = "app" }
      block_device_mappings = {
        xvda = {
          device_name = "/dev/xvda"
          ebs         = { volume_size = 50, volume_type = "gp3", delete_on_termination = true }
        }
      }
    }
  }

  cluster_addons = {
    coredns            = { most_recent = true }
    kube-proxy         = { most_recent = true }
    vpc-cni            = { most_recent = true }
    aws-ebs-csi-driver = { most_recent = true }
  }
}

# ═══════════════════════════════════════════════════════════════════════════════
# ECR — one repo per microservice
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
  image_scanning_configuration { scan_on_push = true }
  encryption_configuration     { encryption_type = "AES256" }
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

# ═══════════════════════════════════════════════════════════════════════════════
# ELASTICACHE REDIS (menu service cache)
# ═══════════════════════════════════════════════════════════════════════════════
resource "aws_elasticache_subnet_group" "redis" {
  name       = "qwikbrew-${local.env}-redis-subnet"
  subnet_ids = module.vpc.private_subnets
}

resource "random_password" "redis_auth" {
  length  = 32
  special = false
}

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id       = "qwikbrew-${local.env}-redis"
  description                = "QwikBrew Redis cache — ${local.env}"
  node_type                  = local.is_prod ? "cache.r6g.large" : "cache.t4g.micro"
  num_cache_clusters         = local.is_prod ? 2 : 1
  automatic_failover_enabled = local.is_prod
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  auth_token                 = random_password.redis_auth.result
  subnet_group_name          = aws_elasticache_subnet_group.redis.name
  security_group_ids         = [aws_security_group.redis.id]
}

resource "aws_security_group" "redis" {
  name   = "qwikbrew-${local.env}-redis-sg"
  vpc_id = module.vpc.vpc_id
  ingress {
    from_port   = 6379
    to_port     = 6379
    protocol    = "tcp"
    cidr_blocks = module.vpc.private_subnets_cidr_blocks
  }
  egress { from_port = 0; to_port = 0; protocol = "-1"; cidr_blocks = ["0.0.0.0/0"] }
}

# ═══════════════════════════════════════════════════════════════════════════════
# AIVEN POSTGRESQL — one service, five logical databases
# ═══════════════════════════════════════════════════════════════════════════════
resource "aiven_pg" "qwikbrew" {
  project      = var.aiven_project
  cloud_name   = var.aiven_cloud_region
  plan         = local.pg_plan
  service_name = "qwikbrew-pg-${local.env}"

  maintenance_window_dow  = "sunday"
  maintenance_window_time = "02:00:00"

  pg_user_config {
    pg_version = "16"

    pg {
      idle_in_transaction_session_timeout = 900
      log_min_duration_statement          = 1000
    }

    pgbouncer {
      pool_mode = "transaction"   # connection pooling via PgBouncer
    }

    ip_filter_object { network = "0.0.0.0/0" }

    backup_hour   = 3
    backup_minute = 0
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
# AIVEN KAFKA — one service, six topics
# ═══════════════════════════════════════════════════════════════════════════════
resource "aiven_kafka" "qwikbrew" {
  project      = var.aiven_project
  cloud_name   = var.aiven_cloud_region
  plan         = local.kafka_plan
  service_name = "qwikbrew-kafka-${local.env}"

  kafka_user_config {
    kafka_version = "3.7"

    kafka {
      auto_create_topics_enable  = false
      num_partitions             = 3
      default_replication_factor = local.is_prod ? 2 : 1
      min_insync_replicas        = local.is_prod ? 2 : 1
      log_retention_hours        = 168
      message_max_bytes          = 1048576
    }

    kafka_rest      = true
    schema_registry = true
    ip_filter_object { network = "0.0.0.0/0" }
  }

  timeouts {
    create = "30m"
    update = "15m"
    delete = "15m"
  }
}

locals {
  kafka_topics = {
    "order-placed"    = { partitions = 3, retention_ms = "604800000" }
    "order-ready"     = { partitions = 3, retention_ms = "604800000" }
    "order-cancelled" = { partitions = 3, retention_ms = "604800000" }
    "wallet-topup"    = { partitions = 3, retention_ms = "604800000" }
    "points-earned"   = { partitions = 3, retention_ms = "604800000" }
    "notifications"   = { partitions = 6, retention_ms = "86400000"  }
  }
}

resource "aiven_kafka_topic" "topics" {
  for_each     = local.kafka_topics
  project      = var.aiven_project
  service_name = aiven_kafka.qwikbrew.service_name
  topic_name   = each.key
  partitions   = each.value.partitions
  replication  = local.is_prod ? 2 : 1
  config {
    retention_ms   = each.value.retention_ms
    cleanup_policy = "delete"
  }
}

resource "aiven_kafka_user" "app" {
  project      = var.aiven_project
  service_name = aiven_kafka.qwikbrew.service_name
  username     = "qwikbrew-app"
  password     = random_password.kafka_password.result
}

resource "random_password" "kafka_password" {
  length  = 32
  special = false
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
# AWS SECRETS MANAGER — stores all Aiven connection strings
# K8s ExternalSecrets reads from here and injects into pods automatically
# ═══════════════════════════════════════════════════════════════════════════════
resource "aws_secretsmanager_secret" "aiven_credentials" {
  name                    = "qwikbrew/${local.env}/aiven-credentials"
  recovery_window_in_days = local.is_prod ? 30 : 0
  description             = "Aiven PostgreSQL and Kafka credentials for QwikBrew ${local.env}"
}

resource "aws_secretsmanager_secret_version" "aiven_credentials" {
  secret_id = aws_secretsmanager_secret.aiven_credentials.id

  secret_string = jsonencode({
    # Postgres — per-service JDBC URLs (SSL required by Aiven)
    PG_USER     = aiven_pg.qwikbrew.service_username
    PG_PASSWORD = aiven_pg.qwikbrew.service_password
    PG_HOST     = aiven_pg.qwikbrew.service_host
    PG_PORT     = tostring(aiven_pg.qwikbrew.service_port)

    USERDB_URL         = "jdbc:postgresql://${aiven_pg.qwikbrew.service_host}:${aiven_pg.qwikbrew.service_port}/userdb?sslmode=require"
    MENUDB_URL         = "jdbc:postgresql://${aiven_pg.qwikbrew.service_host}:${aiven_pg.qwikbrew.service_port}/menudb?sslmode=require"
    ORDERDB_URL        = "jdbc:postgresql://${aiven_pg.qwikbrew.service_host}:${aiven_pg.qwikbrew.service_port}/orderdb?sslmode=require"
    PAYMENTDB_URL      = "jdbc:postgresql://${aiven_pg.qwikbrew.service_host}:${aiven_pg.qwikbrew.service_port}/paymentdb?sslmode=require"
    NOTIFICATIONDB_URL = "jdbc:postgresql://${aiven_pg.qwikbrew.service_host}:${aiven_pg.qwikbrew.service_port}/notificationdb?sslmode=require"

    # Kafka — SASL_SSL (required by Aiven)
    KAFKA_BOOTSTRAP_SERVERS = "${aiven_kafka.qwikbrew.service_host}:${aiven_kafka.qwikbrew.service_port}"
    KAFKA_SECURITY_PROTOCOL = "SASL_SSL"
    KAFKA_SASL_MECHANISM    = "PLAIN"
    KAFKA_SASL_USERNAME     = aiven_kafka_user.app.username
    KAFKA_SASL_PASSWORD     = aiven_kafka_user.app.password
  })

  depends_on = [aiven_pg.qwikbrew, aiven_kafka.qwikbrew, aiven_kafka_user.app]
}

resource "aws_secretsmanager_secret" "app_secrets" {
  name                    = "qwikbrew/${local.env}/app-secrets"
  recovery_window_in_days = local.is_prod ? 30 : 0
}

resource "aws_secretsmanager_secret_version" "app_secrets" {
  secret_id = aws_secretsmanager_secret.app_secrets.id
  secret_string = jsonencode({
    JWT_SECRET             = random_password.jwt_secret.result
    REDIS_AUTH_TOKEN       = random_password.redis_auth.result
    PAYMENT_GATEWAY_KEY_ID = var.payment_gateway_key_id
    PAYMENT_GATEWAY_SECRET = var.payment_gateway_secret
    FCM_SERVER_KEY         = var.fcm_server_key
    SMTP_USER              = var.smtp_user
    SMTP_PASS              = var.smtp_pass
  })
}

resource "random_password" "jwt_secret" {
  length  = 64
  special = false
}

# ═══════════════════════════════════════════════════════════════════════════════
# OUTPUTS
# ═══════════════════════════════════════════════════════════════════════════════
output "cluster_name"    { value = module.eks.cluster_name }
output "cluster_endpoint" { value = module.eks.cluster_endpoint }
output "redis_endpoint"  { value = aws_elasticache_replication_group.redis.primary_endpoint_address }
output "pg_host"         { value = aiven_pg.qwikbrew.service_host }
output "pg_port"         { value = aiven_pg.qwikbrew.service_port }
output "kafka_bootstrap" { value = "${aiven_kafka.qwikbrew.service_host}:${aiven_kafka.qwikbrew.service_port}" }
output "ecr_urls"        { value = { for k, v in aws_ecr_repository.services : k => v.repository_url } }
