(ns blambda.api
  (:require [babashka.deps :refer [clojure]]
            [babashka.curl :as curl]
            [babashka.fs :as fs]
            [blambda.internal :as lib]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [selmer.parser :as selmer]))

(defn build-deps-layer
  "Builds layer for dependencies"
  [{:keys [error deps-path target-dir work-dir] :as opts}]
  (let [deps-zipfile (lib/deps-zipfile opts)]
    (when-not deps-path
      (error "Mising required argument: --deps-path"))

    (fs/create-dirs target-dir work-dir)

    (let [gitlibs-dir "gitlibs"
          m2-dir "m2-repo"
          deps (->> deps-path slurp edn/read-string :deps)]

      (spit (fs/file work-dir "deps.edn")
            {:deps deps
             :mvn/local-repo (str m2-dir)})

      (let [classpath-file (fs/file work-dir "deps-classpath")
            local-classpath-file (fs/file work-dir "deps-local-classpath")
            deps-base-dir (str (fs/path (fs/cwd) work-dir))
            classpath
            (with-out-str
              (clojure ["-Spath"]
                       {:dir work-dir
                        :env (assoc (into {} (System/getenv))
                                    "GITLIBS" (str gitlibs-dir))}))
            deps-classpath (str/replace classpath deps-base-dir "/opt")]
        (println "Classpath before transforming:" classpath)
        (println "Classpath after transforming:" deps-classpath)
        (spit classpath-file deps-classpath)
        (spit local-classpath-file classpath)

        (println "Compressing dependencies layer:" deps-zipfile)
        (let [{:keys [exit err]}
              (sh "zip" "-r" deps-zipfile
                  (fs/file-name gitlibs-dir)
                  (fs/file-name m2-dir)
                  (fs/file-name classpath-file)
                  :dir work-dir)]
          (when (not= 0 exit)
            (println "Error:" err)))))))

(defn build-runtime-layer
  "Builds custom runtime layer"
  [{:keys [bb-arch bb-version target-dir work-dir]
    :as opts}]
  (let [runtime-zipfile (lib/runtime-zipfile opts)
        bb-filename (lib/bb-filename bb-version bb-arch)
        bb-url (lib/bb-url bb-version bb-filename)
        bb-tarball (format "%s/%s" work-dir bb-filename)]
    (doseq [dir [target-dir work-dir]]
      (fs/create-dirs dir))

    (when-not (fs/exists? bb-tarball)
      (println "Downloading" bb-url)
      (io/copy
       (:body (curl/get bb-url {:as :bytes}))
       (io/file bb-tarball)))

    (println "Decompressing" bb-tarball "to" work-dir)
    (sh "tar" "-C" work-dir "-xzf" bb-tarball)

    (doseq [f ["bootstrap" "bootstrap.clj"]]
      (println "Adding file" f)
      (fs/delete-if-exists (format "%s/%s" work-dir f))
      (fs/copy (io/resource f) work-dir))

    (println "Compressing custom runtime layer:" runtime-zipfile)
    (let [{:keys [exit err]}
          (sh "zip" runtime-zipfile
              "bb" "bootstrap" "bootstrap.clj"
              :dir work-dir)]
      (when (not= 0 exit)
        (println "Error:" err)))))

(defn clean
  "Deletes target and work directories"
  [{:keys [target-dir work-dir]}]
  (doseq [dir [target-dir work-dir]]
    (println "Removing directory:" dir)
    (fs/delete-tree dir)))

(defn deploy-deps-layer
  [{:keys [error
           deps-layer-name target-dir]
    :as opts}]
  (when-not deps-layer-name
    (error "Mising required argument: --deps-layer-name"))
  (lib/deploy-layer (merge opts
                           {:layer-filename (lib/deps-zipfile opts)
                            :layer-name deps-layer-name
                            :architectures (lib/deps-layer-architectures opts)
                            :runtimes (lib/deps-layer-runtimes opts)})))

(defn deploy-runtime-layer
  [{:keys [bb-arch runtime-layer-name target-dir] :as opts}]
  (lib/deploy-layer (merge opts
                           {:layer-filename (lib/runtime-zipfile opts)
                            :layer-name runtime-layer-name
                            :architectures (lib/runtime-layer-architectures opts)
                            :runtimes (lib/runtime-layer-runtimes opts)})))

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
     (slurp (io/resource "lambda_layer.tfvars"))
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
  (selmer/render (slurp (io/resource "lambda_layers.tf")) opts))

(defn write-tf-config [{:keys [target-dir tf-config-dir tf-module-dir] :as opts}]
  (let [lambda-layer-config (generate-lambda-layers-config opts)
        lambda-layer-vars (generate-lambda-layer-vars opts)
        lambda-layer-module (generate-lambda-layer-module opts)
        tf-dir (-> (fs/file target-dir tf-config-dir) fs/canonicalize)
        tf-config-file (fs/file tf-dir "blambda.tf")
        tf-vars-file (fs/file tf-dir "blambda.auto.tfvars")
        tf-module-dir (-> (fs/file tf-dir tf-module-dir) fs/canonicalize)
        tf-module-file (fs/file tf-module-dir "lambda_layer.tf")]
    (fs/create-dirs tf-dir)
    (fs/create-dirs tf-module-dir)
    (println "Writing lambda layer config:" (str tf-config-file))
    (spit tf-config-file lambda-layer-config)
    (println "Writing lambda layer vars:" (str tf-vars-file))
    (spit tf-vars-file lambda-layer-vars)
    (println "Writing lambda layers module:" (str tf-module-file))
    (spit tf-module-file lambda-layer-module)))
