# Hello, Blambda!

This is an example of a basic lambda without dependencies. To build and deploy
it, make sure you have Terraform installed (or pull a cheeky `nix-shell -p
terraform` if you're down with Nix), then run:

``` sh
bb blambda build-all
bb blambda terraform write-config
bb blambda terraform apply
```

You should now have a lambda function called **hello-blambda**, which you can
invoke like so (assuming you have the AWS CLI installed):

``` sh
aws lambda invoke --function-name hello-blambda /dev/stdout
```

## JVM backend

If you'd like to use the JVM backend instead of Babashka, you'll need to first
download the [Eclipse
Temurin](https://adoptium.net/temurin/releases/?version=19) JRE. You will also
need to use an S3 bucket to store your lambda layer artifacts due to the large
size of the JVM layer. Update the `bb-jvm.edn` file with the name of the S3
bucket you'd like to use, then run:

``` sh
mkdir -p .work
cp /path/to/OpenJDK19U-jre_aarch64_linux_hotspot_19.0.2_7.tar.gz .work/

bb --config bb-jvm.edn blambda build-all
bb --config bb-jvm.edn blambda terraform write-config

# If using an existing bucket, import it so that Terraform won't try to recreate it
bb --config bb-jvm.edn blambda terraform import-artifacts-bucket

bb --config bb-jvm.edn blambda terraform apply
```
