runtime_layer_name = "{{runtime-layer-name}}"
runtime_layer_compatible_architectures = [
  {% for a in runtime-layer-compatible-architectures %}
  "{{a}}",
  {% endfor %}
]
runtime_layer_compatible_runtimes = [
  {% for r in runtime-layer-compatible-runtimes %}
  "{{r}}",
  {% endfor %}
]
runtime_layer_filename = "{{runtime-layer-filename}}"
{% if use-s3 %}
s3_bucket = "{{s3-bucket}}"
runtime_layer_s3_key = "{{runtime-layer-s3-key}}"
{% endif %}

{% if deps-layer-name %}
deps_layer_name = "{{deps-layer-name}}"
deps_layer_compatible_architectures = [
  {% for a in deps-layer-compatible-architectures %}
  "{{a}}",
  {% endfor %}
]
deps_layer_compatible_runtimes = [
  {% for r in deps-layer-compatible-runtimes %}
  "{{r}}",
  {% endfor %}
]
deps_layer_filename = "{{deps-layer-filename}}"
{% if use-s3 %}
deps_layer_s3_key = "{{deps-layer-s3-key}}"
{% endif %}
{% endif %}
lambda_name = "{{lambda-name}}"
lambda_handler = "{{lambda-handler}}"
lambda_filename = "{{lambda-filename}}"
lambda_iam_role = "{{lambda-iam-role}}"
lambda_memory_size = "{{lambda-memory-size}}"
lambda_runtime = "{{lambda-runtime}}"
lambda_architectures = ["{{lambda-architecture}}"]
{% if use-s3 %}
lambda_s3_key = "{{lambda-s3-key}}"
{% endif %}
