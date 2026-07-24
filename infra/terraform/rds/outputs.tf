output "db_endpoint" {
  description = "Host:port to use when building DB_URL for the backend."
  value       = aws_db_instance.fitlab.endpoint
}

output "db_address" {
  description = "Host only (no port)."
  value       = aws_db_instance.fitlab.address
}

output "db_port" {
  value = aws_db_instance.fitlab.port
}

output "db_name" {
  value = aws_db_instance.fitlab.db_name
}

output "master_username" {
  value = aws_db_instance.fitlab.username
}

output "master_user_secret_arn" {
  description = "Secrets Manager secret holding the generated master password. Fetch it with: aws secretsmanager get-secret-value --secret-id <this-arn> --query SecretString --output text | jq -r .password"
  value       = aws_db_instance.fitlab.master_user_secret[0].secret_arn
}

output "rds_security_group_id" {
  value = aws_security_group.rds.id
}

output "db_url" {
  description = "Ready-to-use Spring DB_URL value (password still comes from the Secrets Manager secret above)."
  value       = "jdbc:postgresql://${aws_db_instance.fitlab.address}:${aws_db_instance.fitlab.port}/${aws_db_instance.fitlab.db_name}"
}
