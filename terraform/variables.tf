variable "aws_region"          { default = "ap-south-1" }
variable "environment"         { default = "staging" }
variable "domain_name"         { default = "" }

variable "aiven_api_token"     { sensitive = true }
variable "aiven_project"       {}
variable "aiven_cloud_region"  { default = "aws-ap-south-1" }

variable "payment_gateway_key_id" {
  default   = ""
  sensitive = true
}
variable "payment_gateway_secret" { 
  default = "" 
  sensitive = true 
}
variable "fcm_server_key"         { 
  default = ""
  sensitive = true 
}
variable "smtp_host"              { 
  default = "email-smtp.ap-south-1.amazonaws.com" 
}
variable "smtp_user"              { 
  default = ""
  sensitive = true 
}
variable "smtp_pass"              { 
  default = ""
  sensitive = true 
}
