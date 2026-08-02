output "cloudfront_domain_name" {
  description = "Serve the frontend from this domain. Should match the CloudFront domain already in DNS/bookmarks (de29liu72e5mg.cloudfront.net) once imported."
  value       = aws_cloudfront_distribution.app.domain_name
}

output "cloudfront_distribution_id" {
  description = "Feed this into the CLOUDFRONT_DISTRIBUTION_ID GitHub secret used by deploy-frontend.yml's cache invalidation step."
  value       = aws_cloudfront_distribution.app.id
}

output "frontend_bucket_name" {
  value = aws_s3_bucket.frontend.bucket
}

output "eb_environment_cname" {
  description = "The backend's own domain - config.js should NOT need this anymore once CloudFront's path-based routing is live; kept only for reference."
  value       = aws_elastic_beanstalk_environment.backend.cname
}
