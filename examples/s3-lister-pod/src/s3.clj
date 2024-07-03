(ns s3
  (:require [babashka.pods :as pods]))

;; Using a pod from the registry looks like this:
(pods/load-pod 'tzzh/aws "0.0.3")

;; Using a pod from the local filesystem looks like this
;; (note that you'll need to remove or comment out the registry
;; version before uncommenting this):
#_(pods/load-pod "pod-tzzh-aws")

(require '[pod.tzzh.s3 :as s3])

(defn list-objects [bucket prefix]
  (-> (s3/list-objects-v2 {:Bucket bucket
                           :Prefix prefix})
      :Contents))

(defn sort-mtime [objects]
  (sort-by :LastModified objects))

(defn summarise [object]
  (select-keys object [:Key :LastModified :Size]))
