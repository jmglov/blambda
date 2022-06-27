# Blambda!

Blambda! is a custom runtime for AWS Lambda that lets you write functions using
Babashka. It is based on the fantastic work that [Tatu
Tarvainen](https://github.com/tatut) did on taking care of the heavy lifting of
interacting with the Lambda runtime API to process function invocations in
[bb-lambda](https://github.com/tatut/bb-lambda). I'm using the
[`bootstrap.clj`](blob/main/bootstrap.clj) from that project directly, and have
just rewritten the machinery around it to remove Docker in favour of zip files,
which I think are simpler (but maybe not easier).

## Building

To build Blambda! with the default Babashka version and platform, run:

``` sh
bb build
```

To see what the default Babashka version and platform are, run:

``` sh
bb help
```

To build a custom runtime with Babashka 0.8.2 on amd64, run:

``` sh
bb build --bb-version 0.8.2 --bb-arch arm64
```

To see what else you can do, run:

``` sh
bb tasks
```

To see what command-line arguments are available, run:

``` sh
bb help
```

## Deploying

To deploy a custom runtime layer, run:

``` sh
bb deploy
```

To deploy an arm64 runtime so that you can use [AWS Graviton 2
lamdbas](https://aws.amazon.com/blogs/compute/migrating-aws-lambda-functions-to-arm-based-aws-graviton2-processors/)
(which AWS say will give you up to "34%" better price performance), run:

``` sh
bb deploy --bb-arch arm64
```

Note that if you do this, you must configure your lambda as follows:
- Runtime: Custom runtime on Amazon Linux 2
- Architecture: arm64

## Using Blambda!

I'm planning on adding examples tasks for deploying layers functions, but for now,
you can do it the hard way with the AWS CLI.

### AWS CLI

This section assumes you have the [AWS Command Line Interface version
1](https://docs.aws.amazon.com/cli/v1/userguide/cli-chap-welcome.html)
installed.

To create or update a layer:

``` sh
bb deploy
```

Assuming you're standing in the root of the Blambda! repo, you will have an
[example](example/) directory that contains a `hello.clj` that looks something
like this:

``` clojure
(ns hello)

(defn hello [{:keys [name] :or {name "Blambda"} :as event} context]
  (prn {:msg "Invoked with event",
        :data {:event event}})
  {:greeting (str "Hello " name "!")})
```

You can create a function that uses Blambda! like this:

``` sh
# The ARN will be printed by the `bb deploy` command
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
