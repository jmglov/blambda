(ns blambda.api.terraform
  (:require [babashka.fs :as fs]
            [babashka.process :refer [shell]]
            [blambda.internal :as lib]
            [clojure.java.io :as io]
            [selmer.parser :as selmer]))

(defn tf-config-path [{:keys [target-dir tf-config-dir] :as opts} filename]
  (let [tf-dir (-> (fs/file target-dir tf-config-dir) fs/canonicalize)]
    (fs/file tf-dir filename)))

(defn generate-lambda-layer-module [opts]
  (selmer/render (slurp (io/resource "lambda_layer.tf")) opts))

(defn generate-lambda-layer-vars
  [{:keys [s3-artifact-path target-dir use-s3
           deps-layer-name] :as opts}]
  (let [zipfile (lib/runtime-zipfile opts)
        filename (fs/file-name zipfile)
        deps-zipfile (when deps-layer-name (lib/deps-zipfile opts))
        deps-filename (when deps-layer-name (fs/file-name deps-zipfile))]
    (selmer/render
     (slurp (io/resource "blambda.tfvars"))
     (merge opts
            {:runtime-layer-compatible-architectures (lib/runtime-layer-architectures opts)
             :runtime-layer-compatible-runtimes (lib/runtime-layer-runtimes opts)
             :runtime-layer-filename zipfile}
            (when use-s3
              {:runtime-layer-s3-key (lib/s3-artifact opts filename)})
            (when deps-layer-name
              {:deps-layer-compatible-architectures (lib/deps-layer-architectures opts)
               :deps-layer-compatible-runtimes (lib/deps-layer-runtimes opts)
               :deps-layer-filename deps-zipfile})
            (when (and deps-layer-name use-s3)
              {:deps-layer-s3-key (lib/s3-artifact opts deps-filename)})))))

(defn generate-lambda-layers-config [opts]
  (selmer/render (slurp (io/resource "blambda.tf")) opts))

(defn run-tf-cmd! [{:keys [tf-config-dir] :as opts} cmd]
  (let [config-file (tf-config-path opts "blambda.tf")]
    (when-not (fs/exists? config-file)
      (throw
       (ex-info
        (format "Missing Terraform config file %s; run `bb blambda terraform write-config`"
                (str config-file))
        {:type :blambda/missing-file
         :filename (str config-file)})))
    (shell {:dir (str (fs/parent config-file))} cmd)))

(defn apply! [opts]
  (run-tf-cmd! opts "terraform init")
  (run-tf-cmd! opts "terraform apply"))

(defn import-s3-bucket! [{:keys [s3-bucket] :as opts}]
  (run-tf-cmd! opts "terraform init")
  (run-tf-cmd! opts (format "terraform import aws_s3_bucket.artifacts %s" s3-bucket)))

(defn write-config [{:keys [target-dir tf-config-dir tf-module-dir] :as opts}]
  (let [lambda-layer-config (generate-lambda-layers-config opts)
        lambda-layer-vars (generate-lambda-layer-vars opts)
        lambda-layer-module (generate-lambda-layer-module opts)
        config-file (tf-config-path opts "blambda.tf")
        vars-file (tf-config-path opts "blambda.auto.tfvars")
        module-dir (tf-config-path opts tf-module-dir)
        module-file (tf-config-path opts (fs/file tf-module-dir "lambda_layer.tf"))]
    (fs/create-dirs module-dir)
    (println "Writing lambda layer config:" (str config-file))
    (spit config-file lambda-layer-config)
    (println "Writing lambda layer vars:" (str vars-file))
    (spit vars-file lambda-layer-vars)
    (println "Writing lambda layers module:" (str module-file))
    (spit module-file lambda-layer-module)))
