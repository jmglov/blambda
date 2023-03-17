runtime_layer_name = "{{runtime-layer-name}}"
{% if not skip-compatible-architectures %}
runtime_layer_compatible_architectures = [
  {% for a in runtime-layer-compatible-architectures %}
  "{{a}}",
  {% endfor %}
]
{% endif %}
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
{% if not skip-compatible-architectures %}
deps_layer_compatible_architectures = [
  {% for a in deps-layer-compatible-architectures %}
  "{{a}}",
  {% endfor %}
]
{% endif %}

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
lambda_memory_size = "{{lambda-memory-size}}"
lambda_runtime = "{{lambda-runtime}}"
{% if lambda-timeout %}
lambda_timeout = {{lambda-timeout}}
{% endif %}
lambda_architectures = ["{{lambda-architecture}}"]
{% if use-s3 %}
lambda_s3_key = "{{lambda-s3-key}}"
{% endif %}
