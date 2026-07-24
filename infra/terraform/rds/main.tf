resource "aws_db_subnet_group" "this" {
  name       = "${var.name}-rds"
  subnet_ids = var.subnet_ids

  tags = {
    Name = "${var.name}-rds"
  }
}

resource "aws_security_group" "rds" {
  name        = "${var.name}-rds"
  description = "Allow Postgres access from the FitLab Elastic Beanstalk instances only"
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.name}-rds"
  }
}

resource "aws_security_group_rule" "from_eb" {
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  security_group_id        = aws_security_group.rds.id
  source_security_group_id = var.eb_security_group_id
  description              = "Postgres from the EB instance security group"
}

resource "aws_security_group_rule" "egress_all" {
  type              = "egress"
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  security_group_id = aws_security_group.rds.id
  cidr_blocks       = ["0.0.0.0/0"]
}

# This resource is intentionally standalone (its own subnet group and security
# group, not tied to the EB environment's lifecycle) so it survives platform
# updates, config changes, and EB environment recreation - see the FitLab
# backend README for why file-based H2 on the EB instance itself is unsafe.
resource "aws_db_instance" "fitlab" {
  identifier     = "${var.name}-db"
  engine         = "postgres"
  engine_version = var.engine_version

  instance_class    = var.instance_class
  allocated_storage = var.allocated_storage
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = var.db_name
  username = var.master_username
  # AWS generates and stores the master password in Secrets Manager;
  # it is never written to the Terraform state file.
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period   = var.backup_retention_period
  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.name}-db-final"

  auto_minor_version_upgrade = true
  apply_immediately          = false

  tags = {
    Name = "${var.name}-db"
  }
}
