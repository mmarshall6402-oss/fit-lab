output "cloudfront_domain_name" {
  description = "Serve the frontend from this domain. Should match the CloudFront domain already in DNS/bookmarks (de29liu72e5mg.cloudfront.net) once imported."
  value       = aws_cloudfront_distribution.app.domain_name
}

output "cloudfront_distribution_id" {
  description = "Feed this into the CLOUDFRONT_DISTRIBUTION_ID GitHub secret used by deploy-frontend.yml's cache invalidation step."
  value       = aws_cloudfront_distribution.app.id
}

output "frontend_bucket_name" {
  value = data.aws_s3_bucket.frontend.bucket
}

output "sns_alerts_topic_arn" {
  value = aws_sns_topic.alerts.arn
}
