(ns favicon
  (:require [babashka.fs :as fs])
  (:import (java.util Base64)))

(def encoder (Base64/getEncoder))

(def favicon-files
  {"android-chrome-192x192.png" "image/png"
   "mstile-150x150.png" "image/png"
   "favicon-16x16.png" "image/png"
   "safari-pinned-tab.svg" "image/svg+xml"
   "favicon.ico" "image/x-icon"
   "site.webmanifest" "application/json"
   "android-chrome-512x512.png" "image/png"
   "apple-touch-icon.png" "image/png"
   "browserconfig.xml" "application/xml"
   "favicon-32x32.png" "image/png"})

(defn read-file [file]
  (.encodeToString encoder (fs/read-all-bytes file)))

(defn serve-favicon [abs-path]
  (let [[_ file] (re-find #"^/(.+)$" abs-path)]
    (when-let [content-type (favicon-files file)]
      {:statusCode 200
       :headers {"Content-Type" content-type}
       :isBase64Encoded true
       :body (read-file file)})))
