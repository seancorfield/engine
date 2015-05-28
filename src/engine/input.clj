;; copyright (c) 2015 Sean Corfield

(ns engine.input
  "Engine input is a collection of data sources."
  (:require [engine.queryable :as q]))

(defprotocol DataSourceCollection
  "Collection of data sources by name."
  (lookup-dsn [this dsn]))

(defrecord EngineInput [dsns default-dsn]

  DataSourceCollection
  (lookup-dsn [this dsn]
    (if-let [ds (get dsns (or dsn default-dsn))]
      ds
      (throw (ex-info (if dsn
                        "No default data source"
                        (str "Unknown data source name: " dsn))
                      {:dsns (keys dsns) :default default-dsn})))))

(defn data-sources
  "Given a map of data sources and a default data source,
  return an engine input."
  [dsns default-dsn]
  (->EngineInput dsns default-dsn))
