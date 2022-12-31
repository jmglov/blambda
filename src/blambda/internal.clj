(ns blambda.internal
  (:require [babashka.fs :as fs]
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
  (format "%s/%s.zip"
          (-> (fs/file target-dir) .getAbsolutePath)
          layer-name))

(defn runtime-zipfile [{:keys [runtime-layer-name] :as opts}]
  (zipfile opts runtime-layer-name))

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
