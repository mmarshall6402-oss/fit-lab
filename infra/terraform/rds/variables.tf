variable "aws_region" {
  description = "AWS region. Must match the region the Elastic Beanstalk environment runs in."
  type        = string
  default     = "us-east-1"
}

variable "name" {
  description = "Name prefix for the RDS instance and its security group."
  type        = string
  default     = "fitlab"
}

variable "db_name" {
  description = "Initial database name created on the instance."
  type        = string
  default     = "fitlab"
}

variable "master_username" {
  description = "Master username for the DB instance (avoid reserved words like 'admin')."
  type        = string
  default     = "fitlab_admin"
}

variable "vpc_id" {
  description = "VPC ID the Elastic Beanstalk environment runs in. Find it under EB console -> environment -> Configuration -> Instances -> EC2 security groups' VPC."
  type        = string
}

variable "subnet_ids" {
  description = "At least two subnet IDs (in different AZs) within var.vpc_id to place the DB subnet group in. Private subnets are strongly preferred over public ones."
  type        = list(string)

  validation {
    condition     = length(var.subnet_ids) >= 2
    error_message = "RDS requires a subnet group spanning at least two Availability Zones."
  }
}

variable "eb_security_group_id" {
  description = "Security group ID attached to the Elastic Beanstalk EC2 instances. Only this security group is allowed to reach the DB on 5432. Find it under EB console -> environment -> Configuration -> Instances -> EC2 security groups."
  type        = string
}

variable "instance_class" {
  description = "RDS instance class. Smallest practical tier by default; bump this up as real traffic arrives."
  type        = string
  default     = "db.t4g.micro"
}

variable "allocated_storage" {
  description = "Allocated storage in GB."
  type        = number
  default     = 20
}

variable "engine_version" {
  description = "PostgreSQL major/minor engine version."
  type        = string
  default     = "16"
}

variable "backup_retention_period" {
  description = "Automated backup retention, in days."
  type        = number
  default     = 7
}

variable "deletion_protection" {
  description = "Prevent accidental deletion of the instance (including via terraform destroy) without first disabling this."
  type        = bool
  default     = true
}
