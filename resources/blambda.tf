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

{% if use-s3 %}
resource "aws_s3_bucket" "artifacts" {
  bucket = var.s3_bucket
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
