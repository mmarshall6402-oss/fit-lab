variable "aws_region" {
  description = "Region the app's AWS resources live in (S3 bucket, Elastic Beanstalk environment)."
  type        = string
  default     = "us-east-1"
}

variable "aws_account_id" {
  description = "Account these resources live in - used only to build the existing S3 bucket name."
  type        = string
  default     = "058914805301"
}

variable "frontend_bucket_name" {
  description = "Existing S3 bucket serving the built frontend. Must match exactly for `terraform import` to adopt it."
  type        = string
  default     = "fitlab-frontend-mark-058914805301-us-east-1-an"
}

variable "cloudfront_price_class" {
  description = "CloudFront price class. PriceClass_100 = US/Canada/Europe edge locations only (cheapest)."
  type        = string
  default     = "PriceClass_100"
}

# Paths CloudFront routes to the backend instead of the S3 frontend bucket.
# Keep in sync with the backend's actual controller mappings.
variable "backend_path_patterns" {
  description = "URL path patterns proxied to the Elastic Beanstalk backend origin."
  type        = list(string)
  default = [
    "/auth/*",
    "/items/*",
    "/outfit/*",
    "/recommend/*",
    "/style-profile/*",
    "/uploads/*",
    "/admin/*",
  ]
}

variable "eb_environment_endpoint" {
  description = "Elastic Beanstalk environment's domain name (CNAME), e.g. fitlab-backend-env.eba-xxxx.us-east-1.elasticbeanstalk.com. Kept as a plain variable, deliberately NOT wired to the aws_elastic_beanstalk_environment resource below, so CloudFront can be planned/applied independently of that higher-risk resource."
  type        = string
  default     = "fitlab-backend-env.eba-tu2mdmhd.us-east-1.elasticbeanstalk.com"
}

variable "eb_application_name" {
  description = "Existing Elastic Beanstalk application name."
  type        = string
  default     = "fitlab-backend"
}

variable "eb_environment_name" {
  description = "Existing Elastic Beanstalk environment name."
  type        = string
  default     = "Fitlab-backend-env"
}

# --- Backend environment variables ---
# These map 1:1 to backend/src/main/resources/application.yml's ${VAR:default}
# placeholders. Populate real values in a terraform.tfvars that is NOT
# committed (see terraform.tfvars.example) - never hardcode secrets here.

variable "admin_token" {
  description = "Shared secret gating /admin/** (ADMIN_TOKEN)."
  type        = string
  sensitive   = true
}

variable "jwt_secret" {
  description = "Signs every account auth token (JWT_SECRET). Rotating this logs out every account."
  type        = string
  sensitive   = true
}

variable "anthropic_api_key" {
  description = "Optional - enables AI auto-tagging/style-extraction features. Left unset, those features fail soft."
  type        = string
  sensitive   = true
  default     = ""
}

variable "db_url" {
  description = "JDBC URL. Defaults to the backend's own local-file H2 default if left empty - only override once you've migrated to a real database (e.g. RDS Postgres)."
  type        = string
  default     = ""
}

variable "db_username" {
  type      = string
  default   = ""
  sensitive = true
}

variable "db_password" {
  type      = string
  default   = ""
  sensitive = true
}
