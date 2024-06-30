# Blambda example: listing S3 objects

This example uses the [awyeah-api](https://github.com/grzm/awyeah-api) library
to list objects in the S3 bucket of your choosing.

## Building and deploying

Make sure you have Terraform installed (or pull a cheeky `nix-shell -p
terraform` if you're down with Nix), then run:

``` text
bb blambda build-all
bb blambda terraform write-config
bb blambda terraform apply
```

This assumes that you have already created an IAM role with an attached policy
that gives your lambda access to the S3 buckets you want to allow listing for,
something like this:

``` json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "logs:CreateLogStream",
                "logs:PutLogEvents"
            ],
            "Resource": "arn:aws:logs:eu-west-1:YOUR_AWS_ACCOUNT:log-group:/aws/lambda/s3-lister:*"
        },
        {
            "Effect": "Allow",
            "Action": "s3:ListBucket",
            "Resource": [
                "arn:aws:s3:::YOUR_S3_BUCKET/*",
                "arn:aws:s3:::ANOTHER_S3_BUCKET/*"
            ]
        }
    ]
}
```

You will also need to update the [bb.edn](bb.edn) in this directory to point at
this role:

``` clojure
:lambda-iam-role "arn:aws:iam::YOUR_AWS_ACCOUNT:role/s3-lister-role"
```

## Testing

Open your newly created s3-lister function in the [AWS
console](https://eu-west-1.console.aws.amazon.com/lambda/home?region=eu-west-1#/functions/s3-lister?tab=testing),
then create a test event like this:

``` json
{
    "bucket": "YOUR_S3_BUCKET",
    "prefix": "some-prefix/"
}
```

If you click the **Test** button and expand the **Details** section, you should
get a response like this:

``` json
{
  "bucket": "YOUR_S3_BUCKET",
  "prefix": "some-prefix/",
  "objects": [
    {
      "Key": "some-prefix/ep-0-1-agile/ep-0-1-agile.transcript.txt",
      "LastModified": "2024-02-09T10:11:00Z",
      "Size": 44823
    },
    {
      "Key": "some-prefix/ep-0-1-agile/ep-0-1-agile.mp3",
      "LastModified": "2024-02-09T10:11:07Z",
      "Size": 63468816
    }
  ]
}
```

The **Log output** should show something like this:

``` text
Starting Babashka:
/opt/bb -cp /var/task:src:/opt/m2-repo/com/cognitect/aws/endpoints/1.1.12.398/endpoints-1.1.12.398.jar:/opt/m2-repo/com/cognitect/aws/s3/825.2.1250.0/s3-825.2.1250.0.jar:/opt/gitlibs/libs/com.grzm/awyeah-api/0fa7dd51f801dba615e317651efda8c597465af6/src:/opt/gitlibs/libs/com.grzm/awyeah-api/0fa7dd51f801dba615e317651efda8c597465af6/resources:/opt/gitlibs/libs/org.babashka/spec.alpha/433b0778e2c32f4bb5d0b48e5a33520bee28b906/src/main/java:/opt/gitlibs/libs/org.babashka/spec.alpha/433b0778e2c32f4bb5d0b48e5a33520bee28b906/src/main/clojure:/opt/gitlibs/libs/org.babashka/spec.alpha/433b0778e2c32f4bb5d0b48e5a33520bee28b906/src/main/resources:/opt/m2-repo/org/clojure/clojure/1.11.1/clojure-1.11.1.jar:/opt/m2-repo/org/clojure/core.specs.alpha/0.2.62/core.specs.alpha-0.2.62.jar:/opt/m2-repo/org/clojure/spec.alpha/0.3.218/spec.alpha-0.3.218.jar /opt/bootstrap.clj
Loading babashka lambda handler: handler/handler
Starting babashka lambda event loop
START RequestId: abd74cfe-1d90-442c-9f29-20812c595cbd Version: $LATEST
{:msg "Invoked with event", :data {:event {:bucket "YOUR_S3_BUCKET", :prefix "some-prefix/"}}}
END RequestId: abd74cfe-1d90-442c-9f29-20812c595cbd
REPORT RequestId: abd74cfe-1d90-442c-9f29-20812c595cbd	Duration: 392.25 ms	Billed Duration: 1190 ms	Memory Size: 512 MB	Max Memory Used: 164 MB	Init Duration: 796.93 ms
```
