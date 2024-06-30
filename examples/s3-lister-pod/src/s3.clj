(ns s3
  (:require [babashka.pods :as pods]))

(pods/load-pod 'tzzh/aws "0.0.3")
(require '[pod.tzzh.s3 :as s3])

(defn list-objects [bucket prefix]
  (-> (s3/list-objects-v2 {:Bucket bucket
                           :Prefix prefix})
      :Contents))

(defn sort-mtime [objects]
  (sort-by :LastModified objects))

(defn summarise [object]
  (select-keys object [:Key :LastModified :Size]))
