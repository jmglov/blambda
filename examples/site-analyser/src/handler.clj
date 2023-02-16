(ns handler
  (:require [cheshire.core :as json]
            [selmer.parser :as selmer]
            [favicon]
            [page-views]
            [util :refer [->map]])
  (:import (java.time LocalDate)
           (java.util UUID)))

(defn get-env
  ([k]
   (or (System/getenv k)
       (let [msg (format "Missing env var: %s" k)]
         (throw (ex-info msg {:msg msg, :env-var k})))))
  ([k default]
   (or (System/getenv k) default)))

(def config {:aws-region (get-env "AWS_REGION" "eu-west-1")
             :views-table (get-env "VIEWS_TABLE")
             :num-days (util/->int (get-env "NUM_DAYS" "7"))
             :num-top-urls (util/->int (get-env "NUM_TOP_URLS" "10"))})

#_(def config {:aws-region (get-env "AWS_REGION" "eu-west-1")
             :views-table "site-analyser-example"
             :num-days (util/->int (get-env "NUM_DAYS" "7"))
             :num-top-urls (util/->int (get-env "NUM_TOP_URLS" "10"))})

(def client (page-views/client config))

(defn serve-dashboard [{:keys [queryStringParameters] :as event}]
  (let [date (:date queryStringParameters)
        dates (if date
                [date]
                (->> (range (:num-days config))
                     (map #(str (.minusDays (LocalDate/now) %)))))
        date-label (or date (format "last %d days" (:num-days config)))
        all-views (mapcat #(page-views/get-views client %) dates)
        total-views (reduce + (map :views all-views))
        top-urls (->> all-views
                      (group-by :url)
                      (map (fn [[url views]]
                             [url (reduce + (map :views views))]))
                      (sort-by second)
                      reverse
                      (take (:num-top-urls config))
                      (map-indexed (fn [i [url views]]
                                     (assoc (->map url views) :rank (inc i)))))
        chart-id (str "div-" (UUID/randomUUID))
        chart-data (->> all-views
                        (group-by :date)
                        (map (fn [[date rows]]
                               {:date date
                                :views (reduce + (map :views rows))}))
                        (sort-by :date))
        chart-spec (json/generate-string
                    {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
                     :data {:values chart-data}
                     :mark {:type "bar"}
                     :width "container"
                     :height 300
                     :encoding {:x {:field "date"
                                    :type "nominal"
                                    :axis {:labelAngle -45}}
                                :y {:field "views"
                                    :type "quantitative"}}})
        tmpl-vars (->map date-label
                         total-views
                         top-urls
                         chart-id
                         chart-spec)]
    (util/log "Rendering dashboard" tmpl-vars)
    {:statusCode 200
     :headers {"Content-Type" "text/html"}
     :body (selmer/render (slurp "index.html")
                          tmpl-vars)}))

(defn track-visit! [{:keys [queryStringParameters] :as event}]
  (let [{:keys [url]} queryStringParameters]
    (if url
      (let [date (str (LocalDate/now))
            url (util/url-decode url)]
        (page-views/increment! client date url)
        {:statusCode 204})
      (do
        (util/log "Missing required query param" {:param "url"})
        {:statusCode 400
         :body "Missing required query param: url"}))))

(comment

  (page-views/increment! client "2023-02-16" "https://example.com/test-repl.html")
  ;; => {:date "2023-02-16", :url "https://example.com/test-repl.html", :new-counter 1}

  )

(defn handler [{:keys [requestContext] :as event} _context]
  (prn {:msg "Invoked with event"
        :data {:event event}})
  (try
    (let [{:keys [method path]} (:http requestContext)
          _ (util/log "Request" (->map method path))
          favicon-res (favicon/serve-favicon path)
          res (if favicon-res
                favicon-res
                (case [method path]
                  ["GET" "/dashboard"] (serve-dashboard event)
                  ["POST" "/track"] (track-visit! event)
                  {:statusCode 404
                   :headers {"Content-Type" "application/json"}
                   :body (json/generate-string {:msg (format "Resource not found: %s" path)})}))]
      (util/log "Sending response" (dissoc res :body))
      res)
    (catch Exception e
      (util/log "Caught exception" (ex-data e))
      {:statusCode 500
       :headers {"Content-Type" "text/plain"}
       :body (ex-message e)})))
