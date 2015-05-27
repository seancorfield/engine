(ns engine.queryable)

(defprotocol Queryable
  "A data source is queryable."
  (-query [this args]))

(defn query [this & args] (-query this args))
