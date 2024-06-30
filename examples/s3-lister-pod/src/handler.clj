(ns handler
  (:require [s3]))

(defn handler [{:keys [bucket prefix] :as event} _context]
  (prn {:msg "Invoked with event"
        :data {:event event}})
  {:bucket bucket
   :prefix prefix
   :objects (->> (s3/list-objects bucket prefix)
                 s3/sort-mtime
                 (map s3/summarise))})
