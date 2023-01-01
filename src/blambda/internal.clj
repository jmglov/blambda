(ns blambda.internal
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn bb-filename [bb-version bb-arch]
  (format "babashka-%s-%s.tar.gz"
          bb-version
          (if (= "arm64" bb-arch)
            "linux-aarch64-static"
            "linux-amd64-static")))

(defn bb-url [bb-version filename]
  (format "https://github.com/babashka/babashka/releases/download/v%s/%s"
          bb-version filename))

(defn zipfile [{:keys [target-dir]} layer-name]
  (fs/file (-> (fs/file target-dir) .getAbsolutePath)
           (format "%s.zip" layer-name)))

(defn runtime-zipfile [{:keys [runtime-layer-name] :as opts}]
  (zipfile opts runtime-layer-name))

(defn lambda-zipfile [{:keys [lambda-name] :as opts}]
  (zipfile opts lambda-name))

(defn deps-zipfile [{:keys [deps-layer-name] :as opts}]
  (zipfile opts deps-layer-name))

(defn runtime-layer-architectures [{:keys [bb-arch]}]
  (if (= "amd64" bb-arch)
    ["x86_64"]
    ["arm64"]))

(defn runtime-layer-runtimes [{:keys [bb-arch]}]
  (concat ["provided.al2"]
          (when (= "amd64" bb-arch)
            ["provided"])))

(defn deps-layer-architectures [_opts]
  ["x86_64" "arm64"])

(defn deps-layer-runtimes [_opts]
  ["provided" "provided.al2"])

(defn s3-artifact [{:keys [s3-artifact-path]} filename]
  (format "%s/%s"
          (str/replace s3-artifact-path #"/$" "")
          filename))

(defn copy-files! [{:keys [source-dir work-dir resource?] :as opts}
                   filenames]
  (doseq [f filenames
          :let [source-file (cond
                              resource? (io/resource f)
                              source-dir (fs/file source-dir f)
                              :else f)
                parent (fs/parent f)]]
    (println "Adding file:" (str f))
    (when parent
      (fs/create-dirs parent))
    (fs/delete-if-exists (fs/file work-dir f))
    (fs/copy source-file work-dir)))
