# Site Analyser

This is an example of a lambda that tracks page visits in a DynamoDB table and
renders a simple HTML dashboard. It's loosely based on the ClojureScript version
described in [Serverless site analytics with Clojure nbb and
AWS](https://www.loop-code-recur.io/simple-site-analytics-with-serverless-clojure/),
by Cyprien Pannier.

It also keeps its artifacts in S3, which is recommended for large files (such as
the custom runtime and deps layers). If you're actually deploying this example,
make sure to replace `:s3-bucket "YOUR-BUCKET-HERE"` in `bb.edn` with the name
of an actual S3 bucket. If the bucket doesn't already exist, make sure to give
it a name that is likely to be unique.

To build and deploy it, make sure you have Terraform installed (or pull a cheeky
`nix-shell -p terraform` if you're down with Nix), then run:

``` sh
bb blambda build-all
bb blambda terraform write-config

# If using an existing S3 bucket for lambda artifacts:
bb blambda terraform import-artifacts-bucket

bb blambda terraform apply
```

If all went well, Terraform will display something like this:

``` text
Apply complete! Resources: 13 added, 0 changed, 0 destroyed.

Outputs:

function_url = "https://fdajhfibugdsx3y5243213b8pa0fvsxy.lambda-url.eu-west-1.on.aws/"
```

The `function_url` is what you'll use to invoke the lambda through its HTTP
interface. If you visit it in your web browser, you should see a dashboard
showing some boring stuff.

## Custom Terraform config

The DynamoDB table and Lambda function URL are defined in `tf/main.tf`, along
with an IAM role and policy for the additional permissions needed by the lambda.
This config is included by setting the `:extra-tf-config ["tf/main.tf"]` key in
`site-analyser/bb.edn`. Note that since a custom IAM role is being used, we also
set the `:lambda-iam-role "${aws_iam_role.lambda.arn}"` key.

In the IAM policy, we refer to the CloudWatch log group that was automatically
defined by Blambda:

``` terraform
resource "aws_iam_policy" "lambda" {
  name = "site-analyser-example-lambda"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "${aws_cloudwatch_log_group.lambda.arn}:*"
      },
      ...
```
