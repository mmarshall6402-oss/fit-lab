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
  description = "Existing S3 bucket serving the built frontend. Referenced via a data source, not managed - see s3.tf."
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
  description = "Elastic Beanstalk environment's domain name (CNAME), e.g. fitlab-backend-env.eba-xxxx.us-east-1.elasticbeanstalk.com. Deliberately a plain string, not read from an aws_elastic_beanstalk_environment resource/data source - this repo intentionally doesn't manage EB in Terraform (see README.md's brownfield note), so CloudFront can be planned/applied independently of it."
  type        = string
  default     = "fitlab-backend-env.eba-tu2mdmhd.us-east-1.elasticbeanstalk.com"
}

variable "eb_environment_name" {
  description = "Existing Elastic Beanstalk environment name - used only as a CloudWatch alarm dimension, never to manage the environment itself."
  type        = string
  default     = "Fitlab-backend-env"
}

variable "eb_autoscaling_group_name" {
  description = "EB environment's underlying Auto Scaling Group name (find via `aws elasticbeanstalk describe-environment-resources --environment-name <name>`). Optional - the CPU alarm is skipped entirely while this is empty."
  type        = string
  default     = ""
}

variable "cloudfront_origin_verify_secret" {
  description = "Shared secret CloudFront sends as X-Origin-Verify on every request to the backend origin; the backend (OriginVerifyFilter) rejects requests missing it, so the EB URL can't be hit directly, bypassing CloudFront. Generate with e.g. `openssl rand -hex 32`. Must also be set as ORIGIN_VERIFY_SECRET on the backend environment."
  type        = string
  sensitive   = true
}

variable "alarm_email" {
  description = "Email subscribed to the fitlab-alerts SNS topic for CloudWatch alarms (EB health, 5xx rate, latency, CPU)."
  type        = string
  default     = "mmarshall1011@icloud.com"
}
