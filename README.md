# Blambda

Blambda is a custom runtime for AWS Lambda that lets you write functions using
Babashka. It is based on the fantastic work that [Tatu
Tarvainen](https://github.com/tatut) did on taking care of the heavy lifting of
interacting with the Lambda runtime API to process function invocations in
[bb-lambda](https://github.com/tatut/bb-lambda). I'm using the
[`bootstrap.clj`](blob/main/bootstrap.clj) from that project directly, and have
just rewritten the machinery around it to remove Docker in favour of zip files,
which I think are simpler (but maybe not easier).

Blambda also owes a huge debt to Karol Wójcik's awesome [Holy
Lambda](https://github.com/FieryCod/holy-lambda), which is a full-featured and
production-grade runtime for Clojure on AWS Lambda. I've read a lot of Holy
Lambda code to figure out how to do the complicated bits of Babashka-ing on
lambda. 💜

## Using Blambda

Blambda is meant to be used as a library from your Babashka project. The easiest
way to use it is to add tasks to your project's `bb.edn`.

This example assumes a basic `bb.edn` like this:

``` clojure
{:deps {net.jmglov/blambda
        #_"You use the newest SHA here:"
        {:git/url "https://github.com/jmglov/blambda.git"
         :git/sha "b9a8b32c41e72ca3619e8b7ab839eebfb133d79c"}}
 :tasks
 {:requires ([blambda.cli :as blambda])
  blambda {:doc "Controls Blambda runtime and layers"
           :task (blambda/dispatch
                  {:deps-layer-name "hello-deps"
                   :lambda-name "hello"
                   :lambda-handler "hello/hello"
                   :lambda-iam-role "arn:aws:iam::123456789100:role/hello-lambda"
                   :source-files ["hello.clj"]})}}}
```

and a simple lambda function contained in a file `src/hello.clj` looking like
this:

``` clojure
(ns hello)

(defn hello [{:keys [name] :or {name "Blambda"} :as event} context]
  (prn {:msg "Invoked with event",
        :data {:event event}})
  {:greeting (str "Hello " name "!")})
```

You can find more examples in the [examples](examples/) directory in this repo.

## Building

### Custom runtime layer

To build Blambda with the default Babashka version and platform, run:

``` sh
bb blambda build-runtime-layer
```

To see what the default Babashka version and platform are, run:

``` sh
bb blambda build-runtime-layer --help
```

To build a custom runtime with Babashka 0.8.2 on amd64, run:

``` sh
bb blambda build-runtime-layer --bb-version 0.8.2 --bb-arch arm64
```

### Dependencies

All but the most basic lambda functions will depend on Clojure libraries.
Blambda has support for keeping these dependencies in a separate layer so that
your function deployment contains only the source of your lambda itself.

Your lambda should declare its dependencies in `bb.edn` or `deps.edn` as normal;
for example, a lambda function that interacts with S3 using
[awyeah-api](https://github.com/grzm/awyeah-api) might have a `src/bb.edn` that
looks like this:

``` clojure
{:paths ["."]
 :deps {com.cognitect.aws/endpoints {:mvn/version "1.1.12.206"}
        com.cognitect.aws/s3 {:mvn/version "822.2.1109.0"}
        com.grzm/awyeah-api {:git/url "https://github.com/grzm/awyeah-api"
                             :git/sha "0fa7dd51f801dba615e317651efda8c597465af6"}
        org.babashka/spec.alpha {:git/url "https://github.com/babashka/spec.alpha"
                                 :git/sha "433b0778e2c32f4bb5d0b48e5a33520bee28b906"}}}
```

To build your dependencies layer:

``` sh
bb blambda build-deps-layer
```

### Lambda

To build your lambda artifact:

``` sh
bb blambda build-lambda
```

## Deploying

To deploy Blambda using Terraform (recommended), first write the config files:

``` sh
bb blambda terraform write-config
```

If you'd like to use S3 to store your lambda artifacts (layers and lambda
zipfile), run:

``` sh
bb blambda terraform write-config \
  --use-s3 --s3-bucket BUCKET --s3-artifact-path PATH
```

Replace `BUCKET` and `PATH` with the appropriate bucket and path. If the bucket
you specify doesn't exist, it will be created by Terraform the first time you
deploy. If you want to use an existing bucket, you'll need to import the bucket
after you generate your Terraform config:

``` text
bb blambda terraform import-artifacts-bucket --s3-bucket BUCKET
```

You can also include extra Terraform configuration. For example, if you want to
create an IAM role for your lambda, you might have a file called `tf/iam.tf` in
your repo which looks something like this:

``` terraform
resource "aws_iam_role" "hello" {
  name = "site-analyser-lambda"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_policy" "hello" {
  name = "site-analyser-lambda"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject"
        ]
        Resource = [
          "arn:aws:s3:::logs.jmglov.net/logs/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "${aws_cloudwatch_log_group.lambda.arn}:*"
      },
      {
        Effect = "Allow",
        Action = [
          "s3:ListBucket"
        ]
        Resource = [
          "arn:aws:s3:::logs.jmglov.net"
        ]
        Condition = {
          "StringLike": {
            "s3:prefix": "logs/*"
          }
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "hello" {
  role = aws_iam_role.hello.name
  policy_arn = aws_iam_policy.hello.arn
}
```

Note how you can refer to resources defined by Blambda, for example
`${aws_cloudwatch_log_group.lambda.arn}`. You can see what resources are defined
by looking at `resources/blambda.tf` and `resources/lambda_layer.tf`.

You can now use this IAM role with your lambda:

``` sh
bb blambda terraform write-config \
  --extra-tf-config tf/iam.tf \
  --lambda-iam-role '${aws_iam_role.hello.arn}'
```

To deploy, run:

``` text
bb blambda terraform apply
```

To deploy an arm64 runtime so that you can use [AWS Graviton 2
lamdbas](https://aws.amazon.com/blogs/compute/migrating-aws-lambda-functions-to-arm-based-aws-graviton2-processors/)
(which AWS say will give you up to "34%" better price performance), run:

``` sh
bb blambda build-runtime-layer --bb-arch arm64 && \
bb blambda terraform write-config --bb-arch arm64 && \
bb blambda terraform apply
```

Note that if you do this, you must configure your lambda as follows:
- Runtime: Custom runtime on Amazon Linux 2
- Architecture: arm64

If you prefer not to use Terraform, you can use the AWS CLI, CloudFormation, or
the AWS console, but Blambda doesn't currently offer any tooling for those
options.
