# The bucket itself already exists and is referenced read-only - a `resource`
# block here would try to CREATE a new bucket with this name and fail on the
# collision. Everything below (public access block, versioning, policy) is
# still directly managed: each is an idempotent PUT against the existing
# bucket (not a distinct object that create-fails-if-present), so these are
# safe to apply without an import step - Terraform just sets them to the
# desired state.
data "aws_s3_bucket" "frontend" {
  bucket = var.frontend_bucket_name
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket = data.aws_s3_bucket.frontend.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "frontend" {
  bucket = data.aws_s3_bucket.frontend.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "fitlab-frontend-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# Grants read access to exactly this CloudFront distribution (scoped by
# AWS:SourceArn), not to the public or to any other distribution. This
# REPLACES whatever bucket policy is currently attached (e.g. an older
# Origin Access Identity grant) - check the bucket's existing policy in the
# console before applying if you're not sure what it currently allows.
resource "aws_s3_bucket_policy" "frontend" {
  bucket = data.aws_s3_bucket.frontend.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AllowCloudFrontOAC"
        Effect    = "Allow"
        Principal = { Service = "cloudfront.amazonaws.com" }
        Action    = "s3:GetObject"
        Resource  = "${data.aws_s3_bucket.frontend.arn}/*"
        Condition = {
          StringEquals = {
            "AWS:SourceArn" = aws_cloudfront_distribution.app.arn
          }
        }
      }
    ]
  })
}
