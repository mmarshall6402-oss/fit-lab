# RDS Postgres for fitlab-backend

Terraform for the standalone RDS Postgres instance the backend needs instead
of the file-based H2 default (see `backend/README.md` for why H2-on-EB loses
data). This is deliberately its own module with its own subnet group and
security group - not something bolted onto the EB environment - so the
database's lifecycle is independent of the EB environment's.

## Prerequisites

- Terraform >= 1.5, AWS provider ~> 5.0
- AWS credentials with permission to create RDS instances, security groups,
  and DB subnet groups in the target account
- The `vpc_id`, `subnet_ids`, and `eb_security_group_id` of the existing
  `Fitlab-backend-env` EB environment (see `terraform.tfvars.example` for
  where to find each one in the EB console)

## Usage

```
cd infra/terraform/rds
cp terraform.tfvars.example terraform.tfvars
# edit terraform.tfvars with your VPC/subnet/security group IDs
terraform init
terraform plan
terraform apply
```

This provisions:

- A dedicated security group that only allows inbound Postgres (5432) from
  the EB instances' security group
- A DB subnet group spanning the subnets you provide
- A `db.t4g.micro` / 20GB gp3 Postgres instance, not publicly accessible,
  encrypted at rest, with a 7-day backup window and deletion protection on
- A master password generated and managed by AWS Secrets Manager (never
  written to Terraform state or any file in this repo)

## Wiring it into the app

After `terraform apply`, fetch the outputs:

```
terraform output db_url
terraform output master_username
terraform output master_user_secret_arn
aws secretsmanager get-secret-value \
  --secret-id "$(terraform output -raw master_user_secret_arn)" \
  --query SecretString --output text | jq -r .password
```

Then, on the EB environment (Configuration -> Updates, monitoring, logging ->
Environment properties), set:

| Property | Value |
|---|---|
| `DB_URL` | `terraform output -raw db_url` |
| `DB_DRIVER` | `org.postgresql.Driver` |
| `DB_USERNAME` | `terraform output -raw master_username` |
| `DB_PASSWORD` | the password fetched from Secrets Manager above |

Save the config, let EB apply it, and confirm the app comes up cleanly
against Postgres before merging/deploying the app-side change to `main`.
