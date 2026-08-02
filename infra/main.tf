terraform {
  required_version = ">= 1.10.0" # backend native S3 locking (use_lockfile) needs 1.10+

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Bucket created once, by hand, via infra/bootstrap (see that directory's
  # README/comments) - it can't live in this same state, since this backend
  # block has to point at it before it exists. use_lockfile uses the S3
  # backend's own native locking (conditional writes to a .tflock object), so
  # no separate DynamoDB lock table is needed.
  backend "s3" {
    bucket       = "fitlab-terraform-state-058914805301"
    key          = "fitlab/terraform.tfstate"
    region       = "us-east-1"
    encrypt      = true
    use_lockfile = true
  }
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
