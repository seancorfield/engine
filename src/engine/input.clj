(ns engine.input
  (:require [engine.queryable :as q]))

(defprotocol QueryableCollection
  "Collection of Queryable data sources by name."
  (-query [this dsn args])
  (queryable [this dsn]))

(defn query [this dsn & args] (-query this dsn args))

(defrecord EngineInput [dsns default-dsn]
  QueryableCollection
  (-query [this dsn args]
    (apply q/query (queryable this dsn) args))
  (queryable [this dsn]
    (if-let [ds (get dsns (or dsn default-dsn))]
      ds
      (throw (ex-info (if dsn "No default data source" (str "Unknown data source name: " dsn))
                      {:dsns (keys dsns) :default default-dsn})))))

(defn data-sources
  [dsns default-dsn]
  (->EngineInput dsns default-dsn))
