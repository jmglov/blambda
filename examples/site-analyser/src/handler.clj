(ns handler
  (:require [cheshire.core :as json]))

(defn handler [{:keys [] :as event} _context]
  (prn {:msg "Invoked with event",
        :data {:event event}})
  {:statusCode 200
   :body (json/generate-string {:msg "Something something"})})
