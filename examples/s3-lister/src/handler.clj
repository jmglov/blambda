(ns handler
  (:require [s3]))

(def aws-region (or (System/getenv "AWS_REGION") "eu-west-1"))

(def s3-client (s3/client aws-region))

(defn handler [{:keys [bucket prefix] :as event} _context]
  (prn {:msg "Invoked with event"
        :data {:event event}})
  {:bucket bucket
   :prefix prefix
   :objects (->> (s3/list-objects s3-client bucket prefix)
                 s3/sort-mtime
                 (map s3/summarise))})
