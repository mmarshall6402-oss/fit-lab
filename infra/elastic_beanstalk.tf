# ============================================================================
# DRAFT - DO NOT `terraform apply` UNTIL THIS IS VERIFIED AGAINST THE REAL
# ENVIRONMENT.
#
# This backend still runs on H2 with a local file DB by default
# (jdbc:h2:file:./data/fitlab, see application.yml) unless DB_URL has been
# overridden to point at a real database. That means the running EB
# instance's disk may be the ONLY copy of every account, item, and saved
# outfit in the app. If this resource's arguments don't exactly match the
# environment's current configuration, `terraform apply` can replace the
# environment (new instance, empty disk) instead of adopting it in place.
#
# Before running anything here:
#   1. aws elasticbeanstalk describe-environments \
#        --application-name fitlab-backend --environment-names Fitlab-backend-env
#   2. aws elasticbeanstalk describe-configuration-settings \
#        --application-name fitlab-backend --environment-name Fitlab-backend-env
#   3. Fill in solution_stack_name, tier, and every setting{} block below from
#      that real output - the values here are placeholders/best guesses from
#      the repo (pom.xml java.version, application.yml var names), not a
#      known-good config.
#   4. terraform import, then `terraform plan` and confirm it shows NO
#      changes before ever running `terraform apply`.
#   5. If DB_URL still defaults to the local H2 file, take a snapshot of the
#      instance/EBS volume first regardless - Terraform aside, this is your
#      only copy of the data.
# ============================================================================

resource "aws_elastic_beanstalk_application" "backend" {
  name        = var.eb_application_name
  description = "FIT//LAB Spring Boot backend"
}

resource "aws_elastic_beanstalk_environment" "backend" {
  name        = var.eb_environment_name
  application = aws_elastic_beanstalk_application.backend.name

  # PLACEHOLDER - replace with the exact string from describe-environments
  # (e.g. "64bit Amazon Linux 2023 v4.x.x running Corretto 21").
  solution_stack_name = "64bit Amazon Linux 2023 v4.3.0 running Corretto 21"

  # PLACEHOLDER - confirm SingleInstance vs LoadBalanced from the real env
  # before import; get this wrong and Terraform will try to add/remove a load
  # balancer, which replaces the environment.
  tier = "WebServer"

  setting {
    namespace = "aws:autoscaling:launchconfiguration"
    name      = "InstanceType"
    value     = "t3.micro"
  }

  setting {
    namespace = "aws:elasticbeanstalk:environment"
    name      = "EnvironmentType"
    value     = "SingleInstance"
  }

  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "ADMIN_TOKEN"
    value     = var.admin_token
  }

  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "JWT_SECRET"
    value     = var.jwt_secret
  }

  dynamic "setting" {
    for_each = var.anthropic_api_key != "" ? [var.anthropic_api_key] : []
    content {
      namespace = "aws:elasticbeanstalk:application:environment"
      name      = "ANTHROPIC_API_KEY"
      value     = setting.value
    }
  }

  dynamic "setting" {
    for_each = var.db_url != "" ? [var.db_url] : []
    content {
      namespace = "aws:elasticbeanstalk:application:environment"
      name      = "DB_URL"
      value     = setting.value
    }
  }

  dynamic "setting" {
    for_each = var.db_username != "" ? [var.db_username] : []
    content {
      namespace = "aws:elasticbeanstalk:application:environment"
      name      = "DB_USERNAME"
      value     = setting.value
    }
  }

  dynamic "setting" {
    for_each = var.db_password != "" ? [var.db_password] : []
    content {
      namespace = "aws:elasticbeanstalk:application:environment"
      name      = "DB_PASSWORD"
      value     = setting.value
    }
  }

  # PLACEHOLDER - the real environment almost certainly has an IAM instance
  # profile already (created by EB on first launch, e.g.
  # "aws-elasticbeanstalk-ec2-role"). Pull the real name from
  # describe-configuration-settings rather than assuming this default exists.
  setting {
    namespace = "aws:autoscaling:launchconfiguration"
    name      = "IamInstanceProfile"
    value     = "aws-elasticbeanstalk-ec2-role"
  }

  lifecycle {
    # Deploys replace the running application version out-of-band via
    # .github/workflows/deploy.yml (beanstalk-deploy action), not Terraform -
    # ignore that drift so `terraform plan` doesn't show a diff after every
    # deploy and tempt someone into "fixing" it by rolling the version back.
    ignore_changes = [
      setting,
    ]
  }
}
