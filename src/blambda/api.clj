(ns blambda.api
  (:require [babashka.deps :refer [clojure]]
            [babashka.curl :as curl]
            [babashka.fs :as fs]
            [babashka.process :refer [shell]]
            [blambda.internal :as lib]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn build-deps-layer
  "Builds layer for dependencies"
  [{:keys [error deps-path target-dir work-dir] :as opts}]
  (let [deps-zipfile (lib/deps-zipfile opts)]
    (if (empty? (fs/modified-since deps-zipfile deps-path))
      (println (format "\nNot rebuilding dependencies layer: no changes to %s since %s was last built"
                       (str deps-path) (str deps-zipfile)))
      (do
        (println "\nBuilding dependencies layer:" (str deps-zipfile))
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

            (println "Compressing dependencies layer:" (str deps-zipfile))
            (shell {:dir work-dir}
                   "zip -r" deps-zipfile
                   (fs/file-name gitlibs-dir)
                   (fs/file-name m2-dir)
                   (fs/file-name classpath-file))))))))

(defn build-runtime-layer
  "Builds custom runtime layer"
  [{:keys [bb-arch bb-version target-dir work-dir]
    :as opts}]
  (let [runtime-zipfile (lib/runtime-zipfile opts)
        bb-filename (lib/bb-filename bb-version bb-arch)
        bb-url (lib/bb-url bb-version bb-filename)
        bb-tarball (format "%s/%s" work-dir bb-filename)]
    (if (and (fs/exists? bb-tarball)
             (empty? (fs/modified-since runtime-zipfile bb-tarball)))
      (println "\nNot rebuilding custom runtime layer; no changes to bb version or arch since last built")
      (do
        (println "\nBuilding custom runtime layer:" (str runtime-zipfile))
        (doseq [dir [target-dir work-dir]]
          (fs/create-dirs dir))

        (when-not (fs/exists? bb-tarball)
          (println "Downloading" bb-url)
          (io/copy
           (:body (curl/get bb-url {:as :bytes}))
           (io/file bb-tarball)))

        (println "Decompressing" bb-tarball "to" work-dir)
        (shell "tar -C" work-dir "-xzf" bb-tarball)

        (lib/copy-files! (assoc opts :resource? true)
                         ["bootstrap" "bootstrap.clj"])

        (println "Compressing custom runtime layer:" (str runtime-zipfile))
        (shell {:dir work-dir}
               "zip" runtime-zipfile
               "bb" "bootstrap" "bootstrap.clj")))))

(defn build-lambda [{:keys [lambda-name source-dir source-files
                            target-dir work-dir] :as opts}]
  (when (empty? source-files)
    (throw (ex-info "Missing source-files"
                    {:type :blambda/error})))
  (let [lambda-zipfile (lib/zipfile opts lambda-name)]
    (if (empty? (fs/modified-since lambda-zipfile
                                   (->> source-files
                                        (map (partial fs/file source-dir))
                                        (cons "bb.edn"))))
      (println "\nNot rebuilding lambda artifact; no changes to source files since last built:"
               source-files)
      (do
        (println "\nBuilding lambda artifact:" (str lambda-zipfile))
        (lib/copy-files! opts source-files)
        (println "Compressing lambda:" (str lambda-zipfile))
        (apply shell {:dir work-dir}
               "zip" lambda-zipfile source-files)))))

(defn build-all [{:keys [deps-layer-name] :as opts}]
  (build-runtime-layer opts)
  (when deps-layer-name
    (build-deps-layer opts))
  (build-lambda opts))

(defn clean
  "Deletes target and work directories"
  [{:keys [target-dir work-dir]}]
  (doseq [dir [target-dir work-dir]]
    (println "Removing directory:" dir)
    (fs/delete-tree dir)))
