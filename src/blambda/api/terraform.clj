(ns blambda.api.terraform
  (:require [babashka.fs :as fs]
            [babashka.process :refer [shell]]
            [blambda.internal :as lib]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [selmer.parser :as selmer]))

(defn tf-config-path [{:keys [target-dir tf-config-dir] :as opts} filename]
  (let [tf-dir (-> (fs/file target-dir tf-config-dir) fs/canonicalize)]
    (fs/file tf-dir filename)))

(defn generate-module [opts]
  (selmer/render (slurp (io/resource "lambda_layer.tf")) opts))

(defn generate-vars
  [{:keys [s3-artifact-path target-dir use-s3
           deps-layer-name lambda-env-vars] :as opts}]
  (let [runtime-zipfile (lib/runtime-zipfile opts)
        runtime-filename (fs/file-name runtime-zipfile)
        lambda-zipfile (lib/lambda-zipfile opts)
        lambda-filename (fs/file-name lambda-zipfile)
        deps-zipfile (when deps-layer-name (lib/deps-zipfile opts))
        deps-filename (when deps-layer-name (fs/file-name deps-zipfile))
        env-vars (->> lambda-env-vars
                      (map #(let [[k v] (str/split % #"=")]
                              {:key k
                               :val v})))]
    (selmer/render
     (slurp (io/resource "blambda.tfvars"))
     (merge opts
            {:runtime-layer-compatible-architectures (lib/runtime-layer-architectures opts)
             :runtime-layer-compatible-runtimes (lib/runtime-layer-runtimes opts)
             :runtime-layer-filename runtime-zipfile
             :lambda-filename lambda-zipfile
             :lambda-architecture (first (lib/runtime-layer-architectures opts))
             :lambda-env-vars env-vars}
            (when use-s3
              {:lambda-s3-key (lib/s3-artifact opts lambda-filename)
               :runtime-layer-s3-key (lib/s3-artifact opts runtime-filename)})
            (when deps-layer-name
              {:deps-layer-compatible-architectures (lib/deps-layer-architectures opts)
               :deps-layer-compatible-runtimes (lib/deps-layer-runtimes opts)
               :deps-layer-filename deps-zipfile})
            (when (and deps-layer-name use-s3)
              {:deps-layer-s3-key (lib/s3-artifact opts deps-filename)})))))

(defn generate-config [opts]
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

(defn write-config [{:keys [lambda-name tf-module-dir extra-tf-config target-dir]
                     :as opts}]
  (let [opts (assoc opts
                    :lambda-filename (format "%s.zip" lambda-name))
        lambda-layer-config (generate-config opts)
        lambda-layer-vars (generate-vars opts)
        lambda-layer-module (generate-module opts)
        config-file (tf-config-path opts "blambda.tf")
        vars-file (tf-config-path opts "blambda.auto.tfvars")
        module-dir (tf-config-path opts tf-module-dir)
        module-file (tf-config-path opts (fs/file tf-module-dir "lambda_layer.tf"))]
    (when-not (empty? extra-tf-config)
      (fs/create-dirs target-dir)
      (doseq [f extra-tf-config
              :let [filename (fs/file-name f)
                    target (fs/file target-dir filename)]]
        (println "Copying Terraform config" (str f))
        (fs/delete-if-exists target)
        (fs/copy f target-dir)))
    (fs/create-dirs module-dir)
    (println "Writing lambda layer config:" (str config-file))
    (spit config-file lambda-layer-config)
    (println "Writing lambda layer vars:" (str vars-file))
    (spit vars-file lambda-layer-vars)
    (println "Writing lambda layers module:" (str module-file))
    (spit module-file lambda-layer-module)))
