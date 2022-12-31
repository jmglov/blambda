variable "runtime_layer_name" {}
variable "runtime_layer_compatible_architectures" {}
variable "runtime_layer_compatible_runtimes" {}
variable "runtime_layer_filename" {}
{% if use-s3 %}
variable "s3_bucket" {}
variable "runtime_layer_s3_key" {}
{% endif %}
{% if deps-layer-name %}
variable "deps_layer_name" {}
variable "deps_layer_compatible_architectures" {}
variable "deps_layer_compatible_runtimes" {}
variable "deps_layer_filename" {}
{% if use-s3 %}
variable "deps_layer_s3_key" {}
{% endif %}
{% endif %}

variable "lambda_name" {}
variable "lambda_handler" {}
variable "lambda_filename" {}
variable "lambda_iam_role" {}
variable "lambda_memory_size" {}
variable "lambda_runtime" {}
variable "lambda_architectures" {}
{% if use-s3 %}
variable "lambda_s3_key" {}
{% endif %}

{% if use-s3 %}
resource "aws_s3_bucket" "artifacts" {
  bucket = var.s3_bucket
}

resource "aws_s3_object" "lambda" {
  bucket = var.s3_bucket
  key = var.lambda_s3_key
  source = var.lambda_filename
  etag = filemd5(var.lambda_filename)
}
{% endif %}

module "runtime" {
  source = "./{{tf-module-dir}}"

  layer_name = var.runtime_layer_name
  compatible_architectures = var.runtime_layer_compatible_architectures
  compatible_runtimes = var.runtime_layer_compatible_runtimes
  filename = var.runtime_layer_filename
{% if use-s3 %}
  s3_bucket = aws_s3_bucket.artifacts.bucket
  s3_key = var.runtime_layer_s3_key
{% endif %}
}

{% if deps-layer-name %}
module "deps" {
  source = "./{{tf-module-dir}}"

  layer_name = var.deps_layer_name
  compatible_architectures = var.deps_layer_compatible_architectures
  compatible_runtimes = var.deps_layer_compatible_runtimes
  filename = var.deps_layer_filename
{% if use-s3 %}
  s3_bucket = aws_s3_bucket.artifacts.bucket
  s3_key = var.deps_layer_s3_key
{% endif %}
}
{% endif %}

resource "aws_lambda_function" "lambda" {
  function_name = var.lambda_name
  role = var.lambda_iam_role
  handler = var.lambda_handler
  memory_size = var.lambda_memory_size
  source_code_hash = filebase64sha256(var.lambda_filename)
{% if use-s3 %}
  s3_bucket = aws_s3_object.lambda.bucket
  s3_key = aws_s3_object.lambda.key
{% else %}
  filename = var.lambda_filename
{% endif %}
  runtime = var.lambda_runtime
  architectures = var.lambda_architectures
  layers = [
    module.runtime.arn,
    module.deps.arn
  ]
}
