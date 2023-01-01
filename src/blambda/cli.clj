(ns blambda.cli
  (:require [babashka.cli :as cli]
            [blambda.api :as api]
            [blambda.api.terraform :as api.terraform]
            [clojure.set :as set]
            [clojure.string :as str]))

(def specs
  {:bb-arch
   {:cmds #{:build-runtime-layer :build-all :terraform-write-config}
    :desc "Architecture to target (use amd64 if you don't care)"
    :ref "<arch>"
    :default "amd64"
    :values #{"amd64" "arm64"}}

   :bb-version
   {:cmds #{:build-runtime-layer :build-all :terraform-write-config}
    :desc "Babashka version"
    :ref "<version>"
    :default "1.0.168"}

   :deps-layer-name
   {:cmds #{:build-deps-layer :build-all :terraform-write-config}
    :desc "Name of dependencies layer in AWS"
    :ref "<name>"}

   :deps-path
   {:cmds #{:build-deps-layer :build-all}
    :desc "Path to bb.edn or deps.edn containing lambda deps"
    :ref "<path>"
    :default "src/bb.edn"}

   :extra-tf-config
   {:cmds #{:terraform-write-config}
    :desc "Filenames of additional Terraform files to include"
    :ref "<files>"
    :coerce []
    :default []}

   :lambda-handler
   {:cmds #{:terraform-write-config}
    :desc "Function used to handle requests (example: hello/handler)"
    :ref "<function>"
    :require true}

   :lambda-env-vars
   {:cmds #{:terraform-write-config}
    :desc "Lambda environment variables, specified as key=val pairs"
    :ref "<keyvals>"
    :coerce []
    :default []}

   :lambda-iam-role
   {:cmds #{:terraform-write-config}
    :desc "ARN of custom lambda role (use ${aws_iam_role.name.arn} if defining in your own TF file)"
    :ref "<arn>"}

   :lambda-memory-size
   {:cmds #{:terraform-write-config}
    :desc "Amount of memory to use, in MB"
    :ref "<mb>"
    :default "512"}

   :lambda-name
   {:cmds #{:build-lambda :build-all :terraform-write-config}
    :desc "Name of lambda function in AWS"
    :ref "<name>"
    :require true}

   :lambda-runtime
   {:cmds #{:terraform-write-config}
    :desc "Identifier of the function's runtime (use provided or provided.al2)"
    :ref "<runtime>"
    :default "provided.al2"}

   :runtime-layer-name
   {:cmds #{:build-runtime-layer :build-all :terraform-write-config}
    :desc "Name of custom runtime layer in AWS"
    :ref "<name>"
    :default "blambda"}

   :s3-artifact-path
   {:cmds #{:terraform-write-config}
    :desc "Path in s3-bucket for artifacts (if using S3)"
    :ref "<path>"}

   :s3-bucket
   {:cmds #{:terraform-write-config :terraform-import-artifacts-bucket}
    :desc "Bucket to use for S3 artifacts (if using S3)"
    :ref "<bucket>"}

   :source-dir
   {:cmds #{:build-lambda :build-all}
    :desc "Lambda source directory"
    :ref "<dir>"
    :default "src"}

   :source-files
   {:cmds #{:build-lambda :build-all}
    :desc "List of files to include in lambda artifact; relative to source-dir"
    :ref "file1 file2 ..."
    :require true
    :coerce []}

   :target-dir
   {:desc "Build output directory"
    :ref "<dir>"
    :default "target"}

   :tf-config-dir
   {:cmds #{:terraform-write-config :terraform-import-artifacts-bucket
            :terraform-apply}
    :desc "Directory to write Terraform config into, relative to target-dir"
    :ref "<dir>"
    :default "."}

   :tf-module-dir
   {:cmds #{:terraform-write-config}
    :desc "Directory to write lambda layer Terraform module into, relative to tf-config-dir"
    :ref "<dir>"
    :default "modules"}

   :use-s3
   {:cmds #{:terraform-write-config}
    :desc "If true, use S3 for artifacts when creating layers"
    :coerce :boolean}

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

(defn mk-spec [default-opts cmd-name]
  (let [cmd-specs (->> specs
                       (filter (fn [[_ {:keys [cmds]}]] (contains? cmds cmd-name)))
                       (into {}))]
    (->> (select-keys specs global-opts)
         (merge cmd-specs)
         (apply-defaults default-opts))))

;; TODO: handle sub-subcommands
(defn ->subcommand-help [default-opts {:keys [cmd desc spec]}]
  (let [spec (apply dissoc spec global-opts)]
    (format "%s: %s\n%s" cmd desc
            (cli/format-opts {:spec
                              (apply-defaults default-opts spec)}))))

(defn print-stderr [msg]
  (binding [*out* *err*]
    (println msg)))

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
          :spec (mk-spec default-opts :build-runtime-layer)}
         {:cmd "build-deps-layer"
          :desc "Builds dependencies layer from bb.edn or deps.edn"
          :fn api/build-deps-layer
          :spec (mk-spec default-opts :build-deps-layer)}
         {:cmd "build-lambda"
          :desc "Builds lambda artifact"
          :fn api/build-lambda
          :spec (mk-spec default-opts :build-lambda)}
         {:cmd "build-all"
          :desc "Builds custom runtime, deps layer (if necessary), and lambda artifact"
          :fn api/build-all
          :spec (mk-spec default-opts :build-all)}
         {:cmd ["terraform" "write-config"]
          :desc "Writes Terraform config for Lambda layers"
          :fn api.terraform/write-config
          :spec (mk-spec default-opts :terraform-write-config)}
         {:cmd ["terraform" "apply"]
          :desc "Deploys runtime, deps layer, and lambda artifact"
          :fn api.terraform/apply!
          :spec (mk-spec default-opts :terraform-apply)}
         {:cmd ["terraform" "import-artifacts-bucket"]
          :desc "Imports existing S3 bucket for lambda artifacts"
          :fn api.terraform/import-s3-bucket!
          :spec (mk-spec default-opts :terraform-import-artifacts-bucket)}
         {:cmd "clean"
          :desc "Removes work and target folders"
          :fn api/clean
          :spec (mk-spec default-opts :clean)}]]
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
       (let [err-type (:type (ex-data e))]
         (cond
           (contains? #{:blambda/error :org.babashka/cli} err-type)
           (do
             ;; TODO: print subcommand help here somehow
             (print-stderr (ex-message e))
             (System/exit 1))

           (= :babashka.process/error err-type)
           (let [{:keys [exit]} (ex-data e)]
             ;; Assume that the subprocess has already printed an error message
             (System/exit exit))

           :else
           (throw e)))))))

(defn -main [& args]
  (apply dispatch {} args))
