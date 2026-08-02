terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # State is not remote yet. Before anyone but one person runs this, move to a
  # backend that supports locking (S3 + DynamoDB, or Terraform Cloud) - two
  # people applying against local state at the same time will corrupt it.
  #
  # backend "s3" {
  #   bucket         = "fitlab-terraform-state-058914805301"
  #   key            = "fitlab/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "fitlab-terraform-locks"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region
}

# us-east-1 is required specifically for ACM certificates used by CloudFront,
# regardless of which region the rest of the stack lives in.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
}
