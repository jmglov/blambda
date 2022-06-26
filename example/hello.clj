(ns hello)

(defn hello [{:keys [name] :or {name "Blambda"} :as event} context]
  (prn {:msg "Invoked with event",
        :data {:event event}})
  {:greeting (str "Hello " name "!")})
