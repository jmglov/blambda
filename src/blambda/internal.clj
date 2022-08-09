(ns blambda.internal
  (:require [babashka.fs :as fs]
            [com.grzm.awyeah.client.api :as aws]))
(defn deploy-layer [{:keys [aws-region target-dir
                            layer-name layer-filename runtimes architectures]}]
  (let [client (aws/client {:api :lambda
                            :region aws-region})
        zipfile (fs/read-all-bytes layer-filename)
        request (merge {:LayerName layer-name
                        :Content {:ZipFile zipfile}}
                       (when runtimes {:CompatibleRuntimes runtimes})
                       (when architectures {:CompatibleArchitectures architectures}))
        _ (println "Publishing layer version for layer" layer-name)
        res (aws/invoke client {:op :PublishLayerVersion
                                :request request})]
    (if (:cognitect.anomalies/category res)
      (prn "Error:" res)
      (println "Published layer" (:LayerVersionArn res)))))
