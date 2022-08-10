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
        {:git/sha "c253bf7d2b0bbbe3e53ad276b1c15c53b98d2088"}}
 :tasks
 {:requires ([blambda.cli :as blambda])
  blambda {:doc "Controls Blambda runtime and layers"
           :task (blambda/dispatch)}}}
```

### Building

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

### Deploying

To deploy Blambda, run:

``` sh
bb blambda deploy-runtime-layer
```

To deploy an arm64 runtime so that you can use [AWS Graviton 2
lamdbas](https://aws.amazon.com/blogs/compute/migrating-aws-lambda-functions-to-arm-based-aws-graviton2-processors/)
(which AWS say will give you up to "34%" better price performance), run:

``` sh
bb blambda build-runtime-layer --bb-arch arm64 && \
bb blambda deploy-runtime-layer --bb-arch arm64
```

Note that if you do this, you must configure your lambda as follows:
- Runtime: Custom runtime on Amazon Linux 2
- Architecture: arm64

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
bb blambda build-deps-layer --deps-path src/bb.edn
```

And then to deploy it:

``` sh
bb blambda deploy-deps-layer --deps-layer-name my-lambda-deps
```

## Basic example

I'm planning on adding example tasks for deploying layers and functions, but for
now, you can do it the hard way with the AWS CLI.

### AWS CLI

This section assumes you have the [AWS Command Line Interface version
1](https://docs.aws.amazon.com/cli/v1/userguide/cli-chap-welcome.html)
installed.

Assuming you're standing in the root of the Blambda repo, you will have an
[example](example/) directory that contains a `hello.clj` that looks something
like this:

``` clojure
(ns hello)

(defn hello [{:keys [name] :or {name "Blambda"} :as event} context]
  (prn {:msg "Invoked with event",
        :data {:event event}})
  {:greeting (str "Hello " name "!")})
```

You can create a function that uses Blambda like this:

``` sh
# The ARN will be printed by the `bb blambda deploy-runtime-layer` command
layer_arn=arn:aws:lambda:eu-west-1:123456789100:layer:blambda:1

cd example

zip hello-blambda.zip hello.clj

aws iam create-role \
  --role-name hello-blambda \
  --assume-role-policy-document file://trust.json

# Set this to the value of `Role.Arn` from the output of the previous command
role_arn=arn:aws:iam::123456789100:role/hello-blambda

aws iam create-policy \
  --policy-name hello-blambda \
  --policy-document file://policy.json

# Set this to the value of `Policy.Arn` from the output of the previous command
policy_arn=arn:aws:iam::123456789100:policy/hello-blambda

aws iam attach-role-policy \
  --role-name hello-blambda \
  --policy-arn=$policy_arn 

aws lambda create-function \
  --function-name hello-blambda \
  --runtime provided \
  --role $role_arn \
  --handler hello/hello \
  --layers $layer_arn \
  --zip-file fileb://hello-blambda.zip
```

You can invoke the function like this:

``` sh
aws lambda invoke \
  --function-name hello-blambda \
  --payload '{}' \
  /dev/stdout
```

You should see something like this:

```
{"greeting":"Hello Blambda!"}{
    "StatusCode": 200,
    "ExecutedVersion": "$LATEST"
}
```

Of course, you can also get more personal:

``` sh
aws lambda invoke \
  --function-name hello-blambda \
  --payload '{"name": "Josh"}' \
  /dev/stdout
```

```
{"greeting":"Hello Josh!"}{
    "StatusCode": 200,
    "ExecutedVersion": "$LATEST"
}
```
