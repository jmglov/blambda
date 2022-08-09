(ns blambda.cli
  (:require [babashka.cli :as cli]))

(def spec
  {:aws-region {:desc "AWS region"
                :ref "<region>"
                :default (or (System/getenv "AWS_DEFAULT_REGION") "eu-west-1")}
   :bb-version {:desc "Babashka version"
                :ref "<version>"
                :default "0.9.161"}
   :bb-arch {:desc "Architecture to target"
             :default "amd64"
             :values ["amd64" "arm64"]}
   :deps-path {:desc "Path to bb.edn or deps.edn containing lambda dependencies"}
   :deps-layer-name {:desc "Name of dependencies layer in AWS"}
   :runtime-layer-name {:desc "Name of custom runtime layer in AWS"
                        :default "blambda"}
   :target-dir {:desc "Build output directory"
                :default "target"}
   :work-dir {:desc "Working directory"
              :default ".work"}})

(defn print-help-and-exit
  ([]
   (print-help-and-exit 0))
  ([exit-code]
   (println (cli/format-opts {:spec spec}))
   (System/exit exit-code)))

(defn error [msg]
  (println msg)
  (print-help-and-exit 1))

(defn parse-opts
  ([]
   (parse-opts *command-line-args*))
  ([args-str]
   (let [opts (cli/parse-opts args-str {:spec spec})]
     (if (:help opts)
       (print-help-and-exit)
       opts))))
