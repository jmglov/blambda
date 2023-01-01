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
