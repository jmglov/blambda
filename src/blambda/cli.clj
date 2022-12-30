(ns blambda.cli
  (:require [babashka.cli :as cli]
            [blambda.api :as api]
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
    :default "1.0.165"}

   :deps-layer-name
   {:desc "Name of dependencies layer in AWS"
    :ref "<name>"}

   :deps-path
   {:desc "Path to bb.edn or deps.edn containing lambda deps"
    :ref "<path>"
    :require true}

   :runtime-layer-name
   {:desc "Name of custom runtime layer in AWS"
    :ref "<name>"
    :default "blambda"}

   :target-dir
   {:desc "Build output directory"
    :ref "<dir>"
    :default "target"}

   :tf-module-dir
   {:desc "Directory to write Terraform module into"
    :ref "<dir>"}

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
   {:cmds [cmd]
    :fn (fn [{:keys [opts]}]
          (let [missing-args (->> (set (keys opts))
                                  (set/difference (set (keys spec)))
                                  (map #(format "--%s" (name %)))
                                  (str/join ", "))]
            (when (:help opts)
              (print-command-help cmd spec)
              (System/exit 0))
            (when-not (empty? missing-args)
              (error {:cmd cmd, :spec spec}
                     (format "Missing required arguments: %s" missing-args)))
            (doseq [[opt {:keys [values]}] spec]
              (when (and values
                         (not (contains? values (opts opt))))
                (error {:cmd cmd, :spec spec}
                       (format "Invalid value for --%s: %s\nValid values: %s"
                               (name opt) (opts opt) (str/join ", " values)))))
            ((:fn cmd-opts) (assoc opts :error (partial error spec)))))}))

(defn mk-table [default-opts]
  (let [cmds
        [{:cmd "build-runtime-layer"
          :desc "Builds Blambda custom runtime layer"
          :fn api/build-runtime-layer
          :spec (mk-spec default-opts #{:bb-version :bb-arch :runtime-layer-name})}
         {:cmd "build-deps-layer"
          :desc "Builds dependencies layer from bb.edn or deps.edn"
          :fn api/build-deps-layer
          :spec (mk-spec default-opts #{:deps-path})}
         {:cmd "deploy-runtime-layer"
          :desc "Deploys Blambda custom runtime layer"
          :fn api/deploy-runtime-layer
          :spec (mk-spec default-opts #{:aws-region :bb-arch :runtime-layer-name})}
         {:cmd "deploy-deps-layer"
          :desc "Deploys dependencies layer"
          :fn api/deploy-deps-layer
          :spec (mk-spec default-opts #{:aws-region :deps-layer-name})}
         {:cmd "write-layer-module"
          :desc "Generates a Terraform module for Lambda layers"
          :fn api/write-lambda-layer-module
          :spec (mk-spec default-opts #{:tf-module-dir :use-s3})}
         {:cmd "clean"
          :desc "Removes work and target folders"
          :fn api/clean}]]
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
       (let [data (ex-data e)]
         (if (= :org.babashka/cli (:type data))
           (do)
           (throw e)))))))

(defn -main [& args]
  (apply dispatch {} args))
