# Site Analyser

This is an example of a lambda that tracks page visits in a DynamoDB table and
renders a simple HTML dashboard. It's loosely based on the ClojureScript version
described in [Serverless site analytics with Clojure nbb and
AWS](https://www.loop-code-recur.io/simple-site-analytics-with-serverless-clojure/),
by Cyprien Pannier.

It also keeps its artifacts in S3, which is recommended for large files (such as
the custom runtime and deps layers). If you're actually deploying this example,
make sure to replace `:s3-bucket "YOUR_HERE"` in `bb.edn` with the name of an
actual S3 bucket. If the bucket doesn't already exist, make sure to give it a
name which will be unique.

To build and deploy it, make sure you have Terraform installed (or pull a cheeky
`nix-shell -p terraform` if you're down with Nix), then run:

``` sh
cd examples/site-analyser

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
interface.

You can track some views like this:

``` sh
# Set the value of BASE_URL to the function_url from the Terraform output
export BASE_URL=https://fdajhfibugdsx3y5243213b8pa0fvsxy.lambda-url.eu-west-1.on.aws

for i in $(seq 0 9); do
  curl -X POST $BASE_URL/track?url=https%3A%2F%2Fexample.com%2Ftest$i.html
done
```

Now if you visit the dashboard in your web browser, you should see some data:

https://fdajhfibugdsx3y5243213b8pa0fvsxy.lambda-url.eu-west-1.on.aws/dashboard

## Blambda configuration

There are a few extra customisations in our `examples/site-analyser/bb.edn` for
this more extensive example, which are described below. `bb.edn` looks something
like this:

``` clojure
{:paths ["."]
 :deps {net.jmglov/blambda {:local/root "../.."}
        #_"You use the newest SHA here:"
        #_{:git/url "https://github.com/jmglov/blambda.git"
           :git/sha "2453e15cf75c03b2b02de5ca89c76081bba40251"}}
 :tasks
 {:requires ([blambda.cli :as blambda])
  :init (do
          (def config {
                       ;; This is the Blambda config stuff
                       }))

  blambda {:doc "Controls Blambda runtime and layers"
           :task (blambda/dispatch config)}}}
```

All of the options described below involve setting a key in the `config` map.

### AWS Graviton2 architecture

[According to
AWS](https://aws.amazon.com/blogs/aws/aws-lambda-functions-powered-by-aws-graviton2-processor-run-your-functions-on-arm-and-get-up-to-34-better-price-performance/),
lambda functions running on Graviton2 processors get much better
price-performance. In order to take advantage of this, we add the following to
`config`:

``` clojure
:arch "arm64"
```

This will inform Blambda to build the custom runtime using the ARM64 version of
Babashka, and create the appropriate architecture and runtime config for lambda
layers and functions.

### Dependencies

This example uses [awyeah-api](https://github.com/grzm/awyeah-api) (a library
which makes Cognitect's [aws-api](https://github.com/cognitect-labs/aws-api)
work with Babashka) to talk to DynamoDB. The appropriate dependencies are
declared in `examples/site-analyser/src/bb.edn`, which should look something
like this:

``` clojure
{:paths ["."]
 :deps {com.cognitect.aws/endpoints {:mvn/version "1.1.12.373"}
        com.cognitect.aws/dynamodb {:mvn/version "825.2.1262.0"}
        com.grzm/awyeah-api {:git/url "https://github.com/grzm/awyeah-api"
                             :git/sha "0fa7dd51f801dba615e317651efda8c597465af6"}
        org.babashka/spec.alpha {:git/url "https://github.com/babashka/spec.alpha"
                                 :git/sha "433b0778e2c32f4bb5d0b48e5a33520bee28b906"}}}
```

To let Blambda know that it should build a lambda layer for the dependencies, we
add the following to `config`:

``` clojure
:deps-layer-name "site-analyser-example-deps"
```

Blambda will automatically look in `src/bb.edn` to find the dependencies to
include in the layer. If dependencies are specified elsewhere (for example, a
`deps.edn`), you can set `:deps-path` to point to it.

### Custom Terraform config

The DynamoDB table and Lambda function URL are defined in
`examples/site-analyser/tf/main.tf`, along with an IAM role and policy for the
additional permissions needed by the lambda.

The DynamoDB table is defined in `main.tf` something like this:

``` terraform
resource "aws_dynamodb_table" "site_analyser" {
  name = "site-analyser"
  billing_mode = "PAY_PER_REQUEST"
  hash_key = "date"
  range_key = "url"

  attribute {
    name = "date"
    type = "S"
  }

  attribute {
    name = "url"
    type = "S"
  }
}
```

In our custom IAM policy, we want to grant the following permissions:
- Update items in the DynamoDB table to increment counters
- Query the DynamoDB table to read counters
- Create log stream and put events to it (for standard lambda logging)

We've explicitly defined the DynamoDB table in this Terraform file, so we can
refer to it by its resource name, `aws_dynamodb_table.site_analyser`, in the IAM
policy.

For the logging permissions, we'll take advantage of the fact that Blambda will
automatically generate a Cloudwatch log group for us, with resource name
`aws_cloudwatch_log_group.lambda`.

Our IAM policy now looks something like this:

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
      {
        Effect = "Allow"
        Action = [
          "dynamodb:Query",
          "dynamodb:UpdateItem",
        ]
        Resource = aws_dynamodb_table.site_analyser.arn
      }
    ]
  })
}
```

We include this Terraform config by adding the following to `config` in
`bb.edn`:

``` clojure
:extra-tf-config ["tf/main.tf"]
```

Finally, since we're defining a custom IAM role and policy, we need to tell
Blambda to use it instead of generating a default one. We do this by adding the
following to `config`:

``` clojure
:lambda-iam-role "${aws_iam_role.lambda.arn}"
```

### Environment variables

Lambda functions can read their configuration from environment variables. The
site-analyser lambda has four configurable values, which you can see at the top
of `examples/site-analyser/src/handler.clj`:

``` clojure
(def config {:aws-region (get-env "AWS_REGION" "eu-west-1")
             :views-table (get-env "VIEWS_TABLE")
             :num-days (util/->int (get-env "NUM_DAYS" "7"))
             :num-top-urls (util/->int (get-env "NUM_TOP_URLS" "10"))})
```

In our case, the default values for `:aws-region`, `:num-days`, and
`:num-top-urls` are fine, but we need to set `:views-table` so that the lambda
knows which DynamoDB table to use for counters. We can set the `VIEWS_TABLE`
environment variable by adding the following to `config` in `bb.edn`:

``` clojure
:lambda-env-vars ["VIEWS_TABLE=${aws_dynamodb_table.site_analyser.name}"]
```

The strange format for environment variables is to support passing them with
`--lambda-env-vars` on the command line.

### Source files

In addition to the Clojure source files for our lambda, we also have an
`index.html` Selmer template for the dashboard and a bunch of files to provide a
"favicon" for the dashboard. We list them in `config` like so:

``` clojure
:source-files [
               ;; Clojure sources
               "handler.clj"
               "favicon.clj"
               "page_views.clj"
               "util.clj"

               ;; HTML templates
               "index.html"

               ;; favicon
               "android-chrome-192x192.png"
               "mstile-150x150.png"
               "favicon-16x16.png"
               "safari-pinned-tab.svg"
               "favicon.ico"
               "site.webmanifest"
               "android-chrome-512x512.png"
               "apple-touch-icon.png"
               "browserconfig.xml"
               "favicon-32x32.png"
               ]
```

### Use S3 for lambda artifacts

The custom runtime and dependencies layers are quite large in size (22 MB and 5
MB, respectively), so we'll upload them to S3 and instruct lambda to pull them
from there, which is the recommended way to deal with large files. We accomplish
this by adding the following to `config`:

``` clojure
:use-s3 true
:s3-bucket "YOUR_BUCKET"
:s3-artifact-path "lambda-artifacts"
```

Replace `YOUR_BUCKET` with either an existing bucket you own or a new bucket to
create (note that the name must be globally unique). As noted above, if you use
an existing bucket, you'll need to import it into your Terraform state before
deploying:

``` sh
bb blambda terraform write-config
bb blambda terraform import-artifacts-bucket
```

## JVM backend

If you'd like to use the JVM backend instead of Babashka, you'll need to first
download the [Eclipse
Temurin](https://adoptium.net/temurin/releases/?version=19) JRE. After doing
that, run:

``` sh
mkdir -p .work
cp /path/to/OpenJDK19U-jre_aarch64_linux_hotspot_19.0.2_7.tar.gz .work/

bb blambda build-all --backend jvm --deps-path src/deps.edn
bb blambda terraform write-config --backend jvm --deps-path src/deps.edn \
  --lambda-memory-size 2048 --lamdba-timeout 120

# If using an existing bucket, import it so that Terraform won't try to recreate it
bb blambda terraform import-artifacts-bucket

bb blambda terraform apply
```
