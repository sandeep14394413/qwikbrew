variable "aws_region"   { default = "ap-south-1" }
variable "environment"  { default = "staging" }   # staging | prod
variable "domain_name"  { default = "" }

variable "payment_gateway_key_id" { default = "" ; sensitive = true }
variable "payment_gateway_secret" { default = "" ; sensitive = true }
variable "fcm_server_key"         { default = "" ; sensitive = true }
variable "smtp_host"              { default = "email-smtp.ap-south-1.amazonaws.com" }
variable "smtp_user"              { default = "" ; sensitive = true }
variable "smtp_pass"              { default = "" ; sensitive = true }
