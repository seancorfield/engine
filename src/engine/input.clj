;; copyright (c) 2015-2016 Sean Corfield

(ns engine.input
  "Engine input is a collection of data sources."
  (:require [engine.queryable :as q]))

(defprotocol DataSourceCollection
  "Collection of data sources by name."
  (find-dsn [this dsn] "Return the datasource or nil.")
  (get-dsn  [this dsn] "Return the datasource of throw."))

(defrecord EngineInput [dsns default-dsn]

  DataSourceCollection
  (find-dsn [this dsn]
    (get dsns (or dsn default-dsn)))

  (get-dsn [this dsn]
    (or (find-dsn this dsn)
        (throw (ex-info (if dsn
                          "No default data source"
                          (str "Unknown data source name: " dsn))
                        {:dsns (keys dsns) :default default-dsn})))))

(defn data-sources
  "Given a map of data sources and a default data source,
  return an engine input."
  [dsns default-dsn]
  (->EngineInput dsns default-dsn))
