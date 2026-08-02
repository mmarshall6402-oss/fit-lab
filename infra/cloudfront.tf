# Serves the frontend (default behavior -> S3) and proxies API calls to the
# Elastic Beanstalk backend (path-based behaviors -> EB) through the SAME
# HTTPS domain. This is what fixes the mixed-content error: the frontend calls
# same-origin paths instead of the backend's http:// EB URL directly, so
# nothing needs a certificate of its own.

data "aws_cloudfront_cache_policy" "caching_disabled" {
  name = "Managed-CachingDisabled"
}

data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

# Forwards every viewer header except Host - required so the Authorization:
# Bearer <jwt> header reaches the backend. CloudFront's default behavior
# strips headers not in an explicit allowlist, which would silently break
# auth on every proxied request.
data "aws_cloudfront_origin_request_policy" "all_viewer_except_host" {
  name = "Managed-AllViewerExceptHostHeader"
}

resource "aws_cloudfront_distribution" "app" {
  enabled             = true
  comment             = "fitlab frontend + backend"
  default_root_object = "index.html"
  price_class         = var.cloudfront_price_class

  origin {
    domain_name              = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_id                = "s3-frontend"
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
  }

  origin {
    domain_name = var.eb_environment_endpoint
    origin_id   = "eb-backend"

    custom_origin_config {
      http_port  = 80
      https_port = 443
      # EB's default *.elasticbeanstalk.com domain has no certificate of its
      # own, so CloudFront must talk to it over plain HTTP. That's fine here:
      # this hop stays inside AWS's network, not over the public internet -
      # the part that mattered for the browser's mixed-content check is the
      # viewer <-> CloudFront leg, which is HTTPS.
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    target_origin_id       = "s3-frontend"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true
    cache_policy_id        = data.aws_cloudfront_cache_policy.caching_optimized.id
  }

  dynamic "ordered_cache_behavior" {
    for_each = var.backend_path_patterns
    content {
      path_pattern             = ordered_cache_behavior.value
      target_origin_id         = "eb-backend"
      viewer_protocol_policy   = "redirect-to-https"
      allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
      cached_methods           = ["GET", "HEAD"]
      compress                 = true
      cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
      origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
    }
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # No custom domain configured today - using the default *.cloudfront.net
  # certificate. Swap this block for an ACM cert (must be in us-east-1) if a
  # custom domain is added later.
  viewer_certificate {
    cloudfront_default_certificate = true
  }
}
