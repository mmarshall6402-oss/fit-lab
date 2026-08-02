# infra

Terraform for FIT//LAB's AWS resources: the frontend's S3 bucket, the
CloudFront distribution in front of it, and CloudWatch alarms watching the
backend's Elastic Beanstalk environment.

## The brownfield problem, and how this handles it

The S3 bucket and the EB environment already exist, created by hand. A
`resource "aws_s3_bucket"` block with the same name would try to *create* a
second bucket and fail on the name collision - and EB is worse: importing
`aws_elastic_beanstalk_environment` only gets you the environment's own
settings, not the ~15 resources it manages behind the scenes (ASG, launch
template, security groups, instance profile, etc.), so it's an easy way to
end up fighting configuration drift indefinitely.

So this repo takes the simpler path for both:

- **S3 bucket**: referenced via a `data` source (`s3.tf`). Read-only, zero
  collision risk. The bucket's public access block, versioning, and policy
  *are* directly managed as their own resources - each is an idempotent PUT
  against the existing bucket (not a distinct object that fails if one
  already exists), so no import needed there either.
- **Elastic Beanstalk**: not represented in Terraform at all. Its domain name
  is a plain string variable (`eb_environment_endpoint`) that CloudFront's
  origin points at, and its environment name is a plain string variable used
  only as a CloudWatch alarm dimension. Nothing here can modify or replace
  the environment.
- **CloudFront + the S3 policy/PAB/versioning + CloudWatch alarms + the SNS
  topic**: genuinely new resources. Normal `terraform apply`, no import.

If EB configuration ever needs to be Terraform-managed (e.g. because manual
console drift becomes a real problem), that's a separate, deliberate piece of
work - pull the real config first via `aws elasticbeanstalk
describe-configuration-settings` and expect to spend real time reconciling
it, not a quick addition to this config.

## Why this exists

`de29liu72e5mg.cloudfront.net` (the frontend, served over HTTPS) was calling
the backend's raw `http://...elasticbeanstalk.com` URL directly, which
browsers block as mixed content. `cloudfront.tf` fixes this by adding the
backend as a second CloudFront origin and routing the app's API path
patterns to it, so the frontend only ever calls same-origin HTTPS paths.

That alone doesn't secure the backend, though - the EB URL stays directly,
publicly reachable, bypassing CloudFront entirely. `cloudfront.tf` also sends
a secret `X-Origin-Verify` header on every request CloudFront forwards to
EB; the backend needs to check for it and reject anything missing it (see
`backend/.../security/OriginVerifyFilter.java` and the `ORIGIN_VERIFY_SECRET`
env var - since EB isn't Terraform-managed here, that env var has to be set
by hand in the EB console/CLI, matching `cloudfront_origin_verify_secret`
below).

Once applied, `frontend/public/config.js` should go back to
`window.__FITLAB_API_BASE__ = '';`.

## One-time setup: state backend

State lives in S3 with native locking (`use_lockfile`, Terraform >= 1.10 -
no DynamoDB table needed). The bucket that holds it can't be in this same
state (nothing to point the backend block at before the bucket exists), so
it's created once via a separate mini-config:

```
cd infra/bootstrap
terraform init
terraform apply
cd ..
terraform init   # picks up the backend "s3" block in main.tf
```

Never touch `infra/bootstrap` again afterward unless the state bucket itself
needs to change.

## Applying

```
cd infra
cp terraform.tfvars.example terraform.tfvars   # fill in real secrets, don't commit it
terraform init
terraform validate
terraform plan
```

Read the plan before applying. Expected: new resources only (CloudFront
distribution, S3 bucket policy/PAB/versioning, SNS topic + subscription,
CloudWatch alarms/log group). It should **never** show an existing resource
being destroyed or replaced - if it does, stop and figure out why before
applying.

```
terraform apply
```

Then:
1. Set `ORIGIN_VERIFY_SECRET` on the EB environment (console or
   `aws elasticbeanstalk update-environment --option-settings ...`) to the
   same value as `cloudfront_origin_verify_secret` in `terraform.tfvars`.
2. Confirm the SNS email subscription (AWS sends a confirmation email to
   `alarm_email` - alarms are silent until it's clicked).
3. Reset `frontend/public/config.js` to `window.__FITLAB_API_BASE__ = '';`
   and confirm register/login no longer throws a mixed-content error.

## Variables

See `variables.tf` for the full list; `terraform.tfvars.example` covers the
ones you actually need to set (everything else has a working default).
