# infra

Terraform for the three AWS resources FIT//LAB's deploy workflows push into:
the frontend's S3 bucket, the CloudFront distribution in front of it, and the
backend's Elastic Beanstalk environment. All three already exist and were
created by hand - nothing here should ever be applied fresh with `terraform
apply` on an empty state. It must be **imported** first.

## Why this exists

`de29liu72e5mg.cloudfront.net` (the frontend, served over HTTPS) was calling
the backend's raw `http://...elasticbeanstalk.com` URL directly, which
browsers block as mixed content. `cloudfront.tf` fixes this by adding the
backend as a second CloudFront origin and routing the app's API path
patterns to it, so the frontend only ever calls same-origin HTTPS paths.
Once applied, `frontend/public/config.js` should go back to
`window.__FITLAB_API_BASE__ = '';`.

## Order of operations

**1. S3 + CloudFront - safe to do now.** Neither holds persistent app data.

```
cd infra
terraform init
terraform import aws_s3_bucket.frontend fitlab-frontend-mark-058914805301-us-east-1-an
terraform import aws_s3_bucket_public_access_block.frontend fitlab-frontend-mark-058914805301-us-east-1-an
terraform import aws_s3_bucket_versioning.frontend fitlab-frontend-mark-058914805301-us-east-1-an
terraform import aws_cloudfront_origin_access_control.frontend <existing-OAC-id-if-any>
terraform import aws_cloudfront_distribution.app <distribution-id>   # from the CLOUDFRONT_DISTRIBUTION_ID GitHub secret
```

If the distribution doesn't already have an Origin Access Control (older
distributions used Origin Access Identity instead), skip importing the OAC
and expect `terraform plan` to show it as a new resource to create instead -
that's fine, OACs are the current recommended approach and this is additive.

Run `terraform plan` and read it end to end before applying anything. It's
expected to show the new `eb-backend` origin and the `ordered_cache_behavior`
blocks as additions - it should **not** show the S3 bucket, its policy, or
the existing default behavior being destroyed or replaced. If it does, stop
and reconcile the `.tf` files against reality before applying.

**2. Elastic Beanstalk - do not import or apply yet.** See the large warning
at the top of `elastic_beanstalk.tf`. This backend defaults to a local H2
file database living on that instance's disk - a bad import here risks
Terraform replacing the environment (new instance, empty disk) instead of
adopting it. Pull the real configuration first:

```
aws elasticbeanstalk describe-environments \
  --application-name fitlab-backend --environment-names Fitlab-backend-env
aws elasticbeanstalk describe-configuration-settings \
  --application-name fitlab-backend --environment-name Fitlab-backend-env
```

Update `elastic_beanstalk.tf`'s `solution_stack_name`, `tier`, and every
`setting` block to match that output exactly, *then* import, then confirm
`terraform plan` shows no changes before ever applying.

## Variables

Copy `terraform.tfvars.example` to `terraform.tfvars` (gitignored) and fill
in real secrets. Never commit `terraform.tfvars`.

## State

No remote backend is configured yet (see the commented-out `backend "s3"`
block in `main.tf`) - state is local. Fine for one person doing the import
carefully; move to a remote backend with locking before anyone else touches
this, or two applies at once will corrupt state.
