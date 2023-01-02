(ns util
  (:import (java.net URLDecoder)
           (java.nio.charset StandardCharsets)))

(defmacro ->map [& ks]
  (assert (every? symbol? ks))
  (zipmap (map keyword ks)
          ks))

(defn ->int [s]
  (Integer/parseUnsignedInt s))

(defn url-decode [s]
  (URLDecoder/decode s StandardCharsets/UTF_8))

(defn lazy-concat [colls]
  (lazy-seq
   (when-first [c colls]
     (lazy-cat c (lazy-concat (rest colls))))))

(defn log
  ([msg]
   (log msg {}))
  ([msg data]
   (prn (assoc data :msg msg))))
