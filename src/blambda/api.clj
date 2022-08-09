(ns blambda.api
  (:require [babashka.deps :refer [clojure]]
            [babashka.curl :as curl]
            [babashka.fs :as fs]
            [blambda.cli :as cli]
            [blambda.internal :as lib]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
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

(defn deps-zipfile [target-dir]
  (str (-> (fs/file target-dir) .getAbsolutePath)
       "/deps.zip"))

(defn layer-zipfile [target-dir]
  (str (-> (fs/file target-dir) .getAbsolutePath)
       "/bb.zip"))

(defn build-deps-layer
  "Builds layer for dependencies"
  ([]
   (build-deps-layer (cli/parse-opts)))
  ([{:keys [deps-path target-dir work-dir]}]
   (let [deps-zipfile (deps-zipfile target-dir)]
     (when-not deps-path
       (cli/error "Mising required argument: --deps-path"))

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

         (println "Compressing custom runtime layer:" deps-zipfile)
         (let [{:keys [exit err]}
               (sh "zip" "-r" deps-zipfile
                   (fs/file-name gitlibs-dir)
                   (fs/file-name m2-dir)
                   (fs/file-name classpath-file)
                   :dir work-dir)]
           (when (not= 0 exit)
             (println "Error:" err))))))))

(defn build-runtime-layer
  "Builds custom runtime layer"
  ([]
   (build-runtime-layer (cli/parse-opts)))
  ([{:keys [help
            bb-arch bb-version target-dir work-dir]
     :as opts}]
   (let [layer-zipfile (layer-zipfile target-dir)
         bb-filename (bb-filename bb-version bb-arch)
         bb-url (bb-url bb-version bb-filename)
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

     (println "Compressing custom runtime layer:" layer-zipfile)
     (let [{:keys [exit err]}
           (sh "zip" layer-zipfile
               "bb" "bootstrap" "bootstrap.clj"
               :dir work-dir)]
       (when (not= 0 exit)
         (println "Error:" err))))))

(defn clean
  "Deletes target and work directories"
  ([]
   (clean (cli/parse-opts)))
  ([{:keys [target-dir work-dir]}]
   (doseq [dir [target-dir work-dir]]
     (println "Removing directory:" dir)
     (fs/delete-tree dir))))

(defn deploy-deps-layer
  ([]
   (deploy-deps-layer (cli/parse-opts)))
  ([{:keys [deps-layer-name target-dir] :as opts}]
   (when-not deps-layer-name
     (cli/error "Mising required argument: --deps-layer-name"))
   (lib/deploy-layer (merge opts
                            {:layer-filename (deps-zipfile target-dir)
                             :layer-name deps-layer-name
                             :architectures ["x86_64" "arm64"]
                             :runtimes ["provided" "provided.al2"]}))))

(defn deploy-runtime-layer
  ([]
   (deploy-runtime-layer (cli/parse-opts)))
  ([{:keys [bb-arch runtime-layer-name target-dir] :as opts}]
   (let [lambda-arch (if (= "amd64" bb-arch) "x86_64" "arm64")]
     (lib/deploy-layer (merge opts
                              {:layer-filename (layer-zipfile target-dir)
                               :layer-name runtime-layer-name
                               :architectures [lambda-arch]
                               :runtimes (concat ["provided.al2"]
                                                 (when (= "amd64" bb-arch)
                                                   ["provided"]))})))))
