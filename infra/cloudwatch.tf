# Deliberately does NOT define aws_elastic_beanstalk_environment/application
# resources - see the "brownfield" note in README.md. These alarms watch the
# existing environment purely by name (a string dimension), so they carry
# none of the collision/replacement risk that owning the EB resource would.
#
# EnvironmentHealth/ApplicationRequests5xx/ApplicationLatencyP99 only publish
# if "Enhanced Health Reporting" is turned on for the environment (default
# for platforms created in the last several years, but not guaranteed) -
# check EB console > Configuration > Monitoring if these alarms show
# perpetually INSUFFICIENT_DATA.

resource "aws_sns_topic" "alerts" {
  name = "fitlab-alerts"
}

resource "aws_sns_topic_subscription" "alerts_email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

resource "aws_cloudwatch_metric_alarm" "eb_environment_health" {
  alarm_name         = "fitlab-eb-environment-health"
  alarm_description  = "Elastic Beanstalk reports this environment as Degraded/Severe."
  namespace          = "AWS/ElasticBeanstalk"
  metric_name        = "EnvironmentHealth"
  dimensions         = { EnvironmentName = var.eb_environment_name }
  statistic          = "Maximum"
  period             = 300
  evaluation_periods = 2
  # EB's health enum: Ok=0, Info=1, Warning=5, Degraded=15, Severe=20 (roughly) -
  # >= 15 for two consecutive periods means real trouble, not a blip.
  threshold           = 15
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "eb_5xx_rate" {
  alarm_name          = "fitlab-eb-5xx-rate"
  alarm_description   = "Backend is returning an elevated rate of 5xx responses."
  namespace           = "AWS/ElasticBeanstalk"
  metric_name         = "ApplicationRequests5xx"
  dimensions          = { EnvironmentName = var.eb_environment_name }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 2
  threshold           = 10
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "eb_latency_p99" {
  alarm_name          = "fitlab-eb-latency-p99"
  alarm_description   = "p99 request latency is elevated."
  namespace           = "AWS/ElasticBeanstalk"
  metric_name         = "ApplicationLatencyP99"
  dimensions          = { EnvironmentName = var.eb_environment_name }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  threshold           = 5 # seconds
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# CPU comes from the underlying Auto Scaling Group, not from EB's own
# namespace, and there's no data source that resolves "this EB environment's
# ASG name" without importing EB itself. Opt in by setting
# eb_autoscaling_group_name (find it via
# `aws elasticbeanstalk describe-environment-resources`) once you have it -
# left unset, this alarm simply isn't created.
resource "aws_cloudwatch_metric_alarm" "eb_cpu" {
  count               = var.eb_autoscaling_group_name != "" ? 1 : 0
  alarm_name          = "fitlab-eb-cpu-high"
  alarm_description   = "Instance CPU has been high for 15 minutes straight."
  namespace           = "AWS/EC2"
  metric_name         = "CPUUtilization"
  dimensions          = { AutoScalingGroupName = var.eb_autoscaling_group_name }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# EB log groups default to "Never expire" retention, which is the kind of
# thing that turns into a surprise CloudWatch Logs bill a year in unnoticed.
# This only takes effect once log streaming to CloudWatch is turned on for
# the environment (EB console > Configuration > Monitoring > "CloudWatch
# Logs streaming") - it does not enable streaming itself.
resource "aws_cloudwatch_log_group" "eb_var_log_web" {
  name              = "/aws/elasticbeanstalk/${var.eb_environment_name}/var/log/web.stdout.log"
  retention_in_days = 30
}
