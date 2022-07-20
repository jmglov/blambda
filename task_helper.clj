(ns task-helper
  (:require [babashka.fs :as fs]
            [clojure.string :as string]))

(def defaults
  {:aws-region {:doc "AWS region"
                :default (or (System/getenv "AWS_DEFAULT_REGION") "eu-west-1")}
   :bb-version {:doc "Babashka version"
                :default "0.8.156"}
   :bb-arch {:doc "Architecture to target"
             :default "amd64"
             :values ["amd64" "arm64"]}
   :layer-name {:doc "Name of custom runtime layer in AWS"
                :default "blambda"}
   :target-dir {:doc "Build output directory"
                :default "target"}
   :work-dir {:doc "Working directory"
              :default ".work"}})

(defn help []
  (println (str
            "Available arguments:\n"
            (->> defaults
                 (map (fn [[k v]]
                        (let [arg (str "--" (name k))
                              {:keys [doc default values]} v
                              valid-values (when values
                                             (str " (valid values: "
                                                  (string/join ", " values)
                                                  ")"))]
                          (str "\t" arg "\t" doc " (default: " default ")" valid-values))))
                 (string/join "\n")))))

(defn error [msg]
  (println msg)
  (println)
  (help)
  (System/exit 1))

(defn parse-args []
  (when-not (even? (count *command-line-args*))
    (error "Error: odd number of command-line args"))
  (let [default-args (->> defaults
                          (map (fn [[k {:keys [default]}]] [k default]))
                          (into {}))]
    (->> *command-line-args*
         (partition 2)
         (map (fn [[k v]]
                (let [arg (keyword (string/replace-first k "--" ""))]
                  (when-not (contains? default-args arg)
                    (error (str "Error: invalid argument: " k)))
                  [arg v])))
         (into {})
         (merge default-args))))

(defn layer-zipfile [target-dir]
  (str (-> (fs/file target-dir) .getAbsolutePath)
       "/bb.zip"))

(defn bb-filename [bb-version bb-arch]
  (format "babashka-%s-%s.tar.gz"
          bb-version
          (if (= "arm64" bb-arch)
            "linux-aarch64-static"
            "linux-amd64-static")))

(defn bb-url [bb-version filename]
  (format "https://github.com/babashka/%s/releases/download/v%s/%s"
          (if (string/includes? bb-version "SNAPSHOT") "babashka-dev-builds" "babashka")
          bb-version filename))
