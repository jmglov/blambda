(ns blambda.cli
  (:require [babashka.cli :as cli]
            [blambda.api :as api]
            [clojure.set :as set]
            [clojure.string :as str]))

(def specs
  {:global
   {:target-dir {:desc "Build output directory"
                 :ref "<dir>"
                 :default "target"}
    :work-dir {:desc "Working directory"
                 :ref "<dir>"
               :default ".work"}}
   :deploy
   {:aws-region {:desc "AWS region"
                 :ref "<region>"
                 :default (or (System/getenv "AWS_DEFAULT_REGION") "eu-west-1")}}})

(defn ->subcommand-help [{:keys [cmd desc spec]}]
  (format "%s: %s\n%s" cmd desc (cli/format-opts {:spec spec})))

(defn print-help [cmds]
  (println
   (format
    "Usage: bb blambda <subcommand> <options>

All subcommands support the options:

%s

Subcommands:

%s"
    (cli/format-opts {:spec (:global specs)})
    (->> cmds
         (map ->subcommand-help)
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
  (let [spec (merge (:global specs) spec)]
    (merge
     cmd-opts
     {:cmds [cmd]
      :fn (fn [{:keys [opts]}]
            (let [opts (merge default-opts opts)
                  missing-args (->> (set (keys opts))
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
            ((:fn cmd-opts) (assoc opts :error (partial error spec)))))
      :spec spec})))

(defn mk-table [opts]
  (let [bb-arch {:desc "Architecture to target (use amd64 if you don't care)"
                 :ref "<arch>"
                 :values #{"amd64" "arm64"}}
        cmds
        [{:cmd "build-runtime-layer"
          :desc "Builds Blambda custom runtime layer"
          :fn api/build-runtime-layer
          :spec {:bb-version {:desc "Babashka version"
                              :ref "<version>"
                              :default "0.9.161"}
                 :bb-arch bb-arch}}
         {:cmd "build-deps-layer"
          :desc "Builds dependencies layer from bb.edn or deps.edn"
          :fn api/build-deps-layer
          :spec {:deps-path
                 {:desc "Path to bb.edn or deps.edn containing lambda deps"
                  :ref "<path>"}}}
         {:cmd "deploy-runtime-layer"
          :desc "Deploys Blambda custom runtime layer"
          :fn api/deploy-runtime-layer
          :spec (merge
                 (:deploy specs)
                 {:runtime-layer-name {:desc "Name of custom runtime layer in AWS"
                                       :ref "<name>"
                                       :default "blambda"}
                 :bb-arch bb-arch})}
         {:cmd "deploy-deps-layer"
          :desc "Deploys dependencies layer"
          :fn api/deploy-deps-layer
          :spec (merge
                 (:deploy specs)
                 {:deps-layer-name {:desc "Name of dependencies layer in AWS"
                                    :ref "<name>"}})}
         {:cmd "clean"
          :desc "Removes work and target folders"
          :fn api/clean}]]
    (cons {:cmds ["help"], :fn (fn [& _] (print-help cmds))}
          (map (partial mk-cmd opts) cmds))))

(defn dispatch
  ([]
   (dispatch {}))
  ([opts & args]
   (cli/dispatch (mk-table opts)
                 (or args
                     (seq *command-line-args*)
                     ["help"]))))

(defn -main [& args]
  (apply dispatch {} args))
