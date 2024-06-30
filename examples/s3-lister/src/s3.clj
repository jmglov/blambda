(ns s3
  (:require [com.grzm.awyeah.client.api :as aws]))

(defn client [aws-region]
  (aws/client {:api :s3, :region aws-region}))

(defn list-objects [s3 bucket prefix]
  (-> (aws/invoke s3 {:op :ListObjectsV2
                      :request {:Bucket bucket
                                :Prefix prefix}})
      :Contents))

(defn sort-mtime [objects]
  (sort-by :LastModified objects))

(defn summarise [object]
  (select-keys object [:Key :LastModified :Size]))
