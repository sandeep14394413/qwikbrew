# ── AWS ───────────────────────────────────────────────────────────────────────
variable "aws_region" {
  description = "AWS region"
  default     = "ap-south-1"
}

# ── Aiven ─────────────────────────────────────────────────────────────────────
variable "aiven_api_token" {
  description = "Aiven API token — from console.aiven.io > User > Tokens"
  sensitive   = true
}

variable "aiven_project" {
  description = "Aiven project name — shown top-left in console.aiven.io"
}

variable "aiven_cloud_region" {
  description = "Aiven cloud region — must match your AWS region"
  default     = "aws-ap-south-1"
}

# ── Optional secrets ───────────────────────────────────────────────────────────
variable "payment_gateway_key_id" {
  default   = ""
  sensitive = true
}

variable "payment_gateway_secret" {
  default   = ""
  sensitive = true
}

variable "fcm_server_key" {
  default   = ""
  sensitive = true
}

variable "smtp_user" {
  default   = ""
  sensitive = true
}

variable "smtp_pass" {
  default   = ""
  sensitive = true
}
