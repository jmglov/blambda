(ns page-views
  (:require #?(:bb [com.grzm.awyeah.client.api :as aws]
               :clj [cognitect.aws.client.api :as aws])
            [util :refer [->map]]))

(defn client [{:keys [aws-region] :as config}]
  (assoc config :dynamodb (aws/client {:api :dynamodb, :region aws-region})))

(defn validate-response [res]
  (when (:cognitect.anomalies/category res)
    (let [data (merge (select-keys res [:cognitect.anomalies/category])
                      {:err-msg (:Message res)
                       :err-type (:__type res)})]
      (util/log "DynamoDB request failed" data)
      (throw (ex-info "DynamoDB request failed" data))))
  res)

(defn increment! [{:keys [dynamodb views-table] :as client} date url]
  (let [req {:TableName views-table
             :Key {:date {:S date}
                   :url {:S url}}
             :UpdateExpression "ADD #views :increment"
             :ExpressionAttributeNames {"#views" "views"}
             :ExpressionAttributeValues {":increment" {:N "1"}}
             :ReturnValues "ALL_NEW"}
        _ (util/log "Incrementing page view counter"
                    (->map date url req))
        res (-> (aws/invoke dynamodb {:op :UpdateItem
                                      :request req})
                validate-response)
        new-counter (-> res
                        (get-in [:Attributes :views :N])
                        util/->int)
        ret (->map date url new-counter)]
    (util/log "Page view counter incremented"
              ret)
    ret))

(defn get-query-page [{:keys [dynamodb views-table] :as client}
                      date
                      {:keys [page-num LastEvaluatedKey] :as prev}]
  (when prev
    (util/log "Got page" prev))
  (when (or (nil? prev)
            LastEvaluatedKey)
    (let [page-num (inc (or page-num 0))
          req (merge
               {:TableName views-table
                :KeyConditionExpression "#date = :date"
                :ExpressionAttributeNames {"#date" "date"}
                :ExpressionAttributeValues {":date" {:S date}}}
               (when LastEvaluatedKey
                 {:ExclusiveStartKey LastEvaluatedKey}))
          _ (util/log "Querying page views" (->map date page-num req))
          res (-> (aws/invoke dynamodb {:op :Query
                                        :request req})
                  validate-response)
          _ (util/log "Got response" (->map res))]
      (assoc res :page-num page-num))))

(defn get-views [client date]
  (->> (iteration (partial get-query-page client date)
                  :vf :Items)
       util/lazy-concat
       (map (fn [{:keys [views date url]}]
              {:date (:S date)
               :url (:S url)
               :views (util/->int (:N views))}))))
