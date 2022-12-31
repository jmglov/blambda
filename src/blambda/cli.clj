(ns blambda.cli
  (:require [babashka.cli :as cli]
            [blambda.api :as api]
            [blambda.api.terraform :as api.terraform]
            [clojure.set :as set]
            [clojure.string :as str]))

(def specs
  {:aws-region
   {:desc "AWS region"
    :ref "<region>"
    :default (or (System/getenv "AWS_DEFAULT_REGION") "eu-west-1")}

   :bb-arch
   {:desc "Architecture to target (use amd64 if you don't care)"
    :ref "<arch>"
    :default "amd64"
    :values #{"amd64" "arm64"}}

   :bb-version
   {:desc "Babashka version"
    :ref "<version>"
    :default "1.0.168"}

   :deps-layer-name
   {:desc "Name of dependencies layer in AWS"
    :ref "<name>"}

   :deps-path
   {:desc "Path to bb.edn or deps.edn containing lambda deps"
    :ref "<path>"
    :default "src/bb.edn"}

   :extra-tf-config
   {:desc "Filenames of additional Terraform files to include"
    :ref "<files>"
    :coerce []
    :default []}

   :lambda-handler
   {:desc "Function used to handle requests (example: hello/handler)"
    :ref "<function>"
    :require true}

   :lambda-env-vars
   {:desc "Lambda environment variables, specified as key=val pairs"
    :ref "<keyvals>"
    :coerce []
    :default []}

   :lambda-iam-role
   {:desc "ARN of IAM role (use ${aws_iam_role.name.arn} if defining in your own TF file)"
    :ref "<arn>"
    :require true}

   :lambda-memory-size
   {:desc "Amount of memory to use, in MB"
    :ref "<mb>"
    :default "512"}

   :lambda-name
   {:desc "Name of lambda function in AWS"
    :ref "<name>"
    :require true}

   :lambda-runtime
   {:desc "Identifier of the function's runtime (use provided or provided.al2)"
    :ref "<runtime>"
    :default "provided.al2"}

   :runtime-layer-name
   {:desc "Name of custom runtime layer in AWS"
    :ref "<name>"
    :default "blambda"}

   :source-dir
   {:desc "Lambda source directory"
    :ref "<dir>"
    :default "src"}

   :source-files
   {:desc "List of files to include in lambda artifact; relative to source-dir"
    :ref "file1 file2 ..."
    :require true
    :coerce []}

   :target-dir
   {:desc "Build output directory"
    :ref "<dir>"
    :default "target"}

   :tf-config-dir
   {:desc "Directory to write Terraform config into, relative to target-dir"
    :ref "<dir>"
    :default "."}

   :tf-module-dir
   {:desc "Directory to write lambda layer Terraform module into, relative to tf-config-dir"
    :ref "<dir>"
    :default "modules"}

   :use-s3
   {:desc "If true, use S3 for artifacts when creating layers"
    :coerce :boolean}

   :s3-bucket
   {:desc "Bucket to use for S3 artifacts (if using S3)"
    :ref "<bucket>"}

   :s3-artifact-path
   {:desc "Path in s3-bucket for artifacts (if using S3)"
    :ref "<path>"}

   :work-dir
   {:desc "Working directory"
    :ref "<dir>"
    :default ".work"}})

(def global-opts #{:target-dir :work-dir})

(defn apply-defaults [default-opts spec]
  (->> spec
       (map (fn [[k v]]
              (if-let [default-val (default-opts k)]
                [k (assoc v :default default-val)]
                [k v])))
       (into {})))

(defn mk-spec [default-opts opts]
  (->> (select-keys specs (set/union global-opts opts))
       (apply-defaults default-opts)))

;; TODO: handle sub-subcommands
(defn ->subcommand-help [default-opts {:keys [cmd desc spec]}]
  (let [spec (apply dissoc spec global-opts)]
    (format "%s: %s\n%s" cmd desc
            (cli/format-opts {:spec
                              (apply-defaults default-opts spec)}))))

(defn print-help [default-opts cmds]
  (println
   (format
    "Usage: bb blambda <subcommand> <options>

All subcommands support the options:

%s

Subcommands:

%s"
    (cli/format-opts {:spec (select-keys specs global-opts)})
    (->> cmds
         (map (partial ->subcommand-help default-opts))
         (str/join "\n\n"))))
  (System/exit 0))

(defn print-command-help [cmd spec]
  (println
   (format "Usage: bb blambda %s <options>\n\nOptions:\n%s"
           cmd (cli/format-opts {:spec spec}))))

(defn error [{:keys [cmd spec]} msg]
  (println (format "%s\n" msg))
  (print-command-help cmd spec)
  (System/exit 1))

(defn mk-cmd [default-opts {:keys [cmd spec] :as cmd-opts}]
  (merge
   cmd-opts
   {:cmds (if (vector? cmd) cmd [cmd])
    :fn (fn [{:keys [opts]}]
          (when (:help opts)
            (print-command-help cmd spec)
            (System/exit 0))
          (doseq [[opt {:keys [values]}] spec]
            (when (and values
                       (not (contains? values (opts opt))))
              (error {:cmd cmd, :spec spec}
                     (format "Invalid value for --%s: %s\nValid values: %s"
                             (name opt) (opts opt) (str/join ", " values)))))
          ((:fn cmd-opts) (assoc opts :error (partial error spec))))}))

(defn mk-table [default-opts]
  (let [cmds
        [{:cmd "build-runtime-layer"
          :desc "Builds Blambda custom runtime layer"
          :fn api/build-runtime-layer
          :spec (mk-spec default-opts #{:bb-version :bb-arch :runtime-layer-name})}
         {:cmd "build-deps-layer"
          :desc "Builds dependencies layer from bb.edn or deps.edn"
          :fn api/build-deps-layer
          :spec (mk-spec default-opts #{:deps-path :deps-layer-name})}
         {:cmd "build-lambda"
          :desc "Builds lambda artifact"
          :fn api/build-lambda
          :spec (mk-spec default-opts #{:lambda-name :source-dir :source-files})}
         {:cmd ["terraform" "write-config"]
          :desc "Writes Terraform config for Lambda layers"
          :fn api.terraform/write-config
          :spec (mk-spec default-opts #{:tf-config-dir :tf-module-dir
                                        :runtime-layer-name :deps-layer-name
                                        :lambda-name :lambda-handler
                                        :lambda-iam-role
                                        :lambda-env-vars
                                        :lambda-runtime :lambda-memory-size
                                        :use-s3 :s3-bucket :s3-artifact-path
                                        :extra-tf-config})}
         {:cmd ["terraform" "apply"]
          :desc "Deploys runtime, deps layer, and lambda artifact"
          :fn api.terraform/apply!
          :spec (mk-spec default-opts #{:tf-config-dir})}
         {:cmd ["terraform" "import-artifacts-bucket"]
          :desc "Imports existing S3 bucket for lambda artifacts"
          :fn api.terraform/import-s3-bucket!
          :spec (mk-spec default-opts #{:s3-bucket :tf-config-dir})}
         {:cmd "clean"
          :desc "Removes work and target folders"
          :fn api/clean
          :spec (mk-spec default-opts #{})}]]
    (conj (mapv (partial mk-cmd default-opts) cmds)
          {:cmds [], :fn (fn [m] (print-help default-opts cmds))})))

(defn dispatch
  ([]
   (dispatch {}))
  ([default-opts & args]
   (try
     (cli/dispatch (mk-table default-opts)
                   (or args
                       (seq *command-line-args*)))
     (catch Exception e
       (cond
         (= :blambda/error (:type (ex-data e)))
         (binding [*out* *err*]
           (println (ex-message e))
           ;; TODO: print subcommand help here somehow
           )

         (= :babashka.process/error (:type (ex-data e)))
         (let [{:keys [exit]} (ex-data e)]
           ;; Assume that the subprocess has already printed an error message
           (System/exit exit))

         :else
         (throw e))))))

(defn -main [& args]
  (apply dispatch {} args))
