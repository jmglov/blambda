(ns blambda.api
  (:require [babashka.deps :refer [clojure]]
            [babashka.curl :as curl]
            [babashka.fs :as fs]
            [blambda.internal :as lib]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]))

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
