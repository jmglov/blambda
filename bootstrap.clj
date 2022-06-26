;; AWS Lambda runtime using babashka.
;;
;;  The bootstrap shell script will run this
;;  and set the classpath to the $LAMBDA_TASK_ROOT.

(require '[org.httpkit.client :as http]
         '[clojure.string :as str]
         '[cheshire.core :as cheshire])

(def handler-name (System/getenv "_HANDLER"))
(println "Loading babashka lambda handler: " handler-name)

(def runtime-api-url (str "http://" (System/getenv "AWS_LAMBDA_RUNTIME_API") "/2018-06-01/runtime/"))

(defn throwable->error-body [t]
  {:errorMessage (.getMessage t)
   :errorType (-> t .getClass .getName)
   :stackTrace (mapv str (.getStackTrace t))})

;; load handler
(def handler
  (let [[handler-ns handler-fn] (str/split handler-name #"/")]
    (try
      (require (symbol handler-ns))
      (resolve (symbol handler-ns handler-fn))
      (catch Throwable t
        (println "Unable to run initialize handler fn " handler-fn "in namespace" handler-ns
                 "\nthrow: " t)
        (http/post (str runtime-api-url "init/error")
                   {:body (cheshire/encode
                           (throwable->error-body t))})
        nil))))

(when-not handler
  (http/post (str runtime-api-url "init/error")
             {:headers {"Lambda-Runtime-Function-Error-Type" "Runtime.NoSuchHandler"}
              :body (cheshire/encode {"error" (str handler-name " didn't resolve.")})}))

;; API says not to use timeout when getting next invocation, so make it a long one
(def timeout-ms (* 1000 60 60 24))

(defn next-invocation
  "Get the next invocation, returns payload and fn to respond."
  []
  (let [{:keys [headers body]}
        @(http/get (str runtime-api-url "invocation/next")
                   {:timeout timeout-ms
                    :as :text})
        id (:lambda-runtime-aws-request-id headers)]
    {:event (cheshire/decode body keyword)
     :context headers
     :send-response!
     (fn [response]
       @(http/post (str runtime-api-url "invocation/" id "/response")
                   {:body (cheshire/encode response)}))
     :send-error!
     (fn [thrown]
       @(http/post (str runtime-api-url "invocation/" id "/error")
                   {:body (cheshire/encode
                           (throwable->error-body thrown))}))}))

(when handler
  (println "Starting babashka lambda event loop")
  (loop [{:keys [event context send-response! send-error!]} (next-invocation)]
    (try
      (let [response (handler event context)]
        (send-response! response))
      (catch Throwable t
        (println "Error in executing handler" t)
        (send-error! t)))
    (recur (next-invocation))))
