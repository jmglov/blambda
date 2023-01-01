(ns handler
  (:require [cheshire.core :as json]
            [selmer.parser :as selmer]))

(defn handler [{:keys [requestContext] :as event} _context]
  (prn {:msg "Invoked with event"
        :data {:event event}})
  (let [method (get-in requestContext [:http :method])]
    (case method
      "GET"
      {:statusCode 200
       :headers {"Content-Type" "text/html"}
       :body (selmer/render (slurp "index.html")
                            {:body "Nothing to see here... yet!"})}

      "POST"
      {:statusCode 204}

      {:statusCode 405
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string {:msg (format "Method not allowed: %s" method)})})))
