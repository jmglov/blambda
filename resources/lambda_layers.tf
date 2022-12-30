variable "runtime_layer_name" {}
variable "runtime_layer_filename" {}
variable "runtime_layer_compatible_architectures" {}
variable "runtime_layer_compatible_runtimes" {}
{% if use-s3 %}
variable "s3_bucket"
variable "runtime_layer_s3_object"
{% endif %}
{% if deps-layer-name %}
variable "deps_layer_name" {}
variable "deps_layer_filename" {}
variable "deps_layer_compatible_architectures" {}
variable "deps_layer_compatible_runtimes" {}
{% if use-s3 %}
variable "deps_layer_s3_object"
{% endif %}
{% endif %}

module "runtime" {
  source = "./{{tf-module-dir}}"

  layer_name = var.runtime_layer_name
  source_code_hash = filebase64sha256(var.runtime_layer_filename)
  compatible_architectures = var.runtime_layer_compatible_architectures
  compatible_runtimes = var.runtime_layer_compatible_runtimes
{% if use-s3 %}
  s3_bucket = var.s3_bucket
  s3_object = var.runtime_layer_s3_object
{% else %}
  filename = var.runtime_layer_filename
{% endif %}
}

{% if deps-layer-name %}
module "deps" {
  source = "./{{tf-module-dir}}"

  layer_name = var.deps_layer_name
  source_code_hash = filebase64sha256(var.deps_layer_filename)
  compatible_architectures = var.deps_layer_compatible_architectures
  compatible_runtimes = var.deps_layer_compatible_runtimes
{% if use-s3 %}
  s3_bucket = var.s3_bucket
  s3_object = var.deps_layer_s3_object
{% else %}
  filename = var.deps_layer_filename
{% endif %}
}
{% endif %}
