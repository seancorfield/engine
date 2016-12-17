;; copyright (c) 2015-2016 Sean Corfield

(ns engine.input
  "Engine input is a collection of data sources."
  (:require [engine.queryable :as q]))

(defprotocol DataSourceCollection
  "Collection of data sources by name."
  (find-dsn [this dsn] "Return the datasource or nil.")
  (get-dsn  [this dsn] "Return the datasource or throw."))

(defrecord EngineInput [dsns default-dsn]

  DataSourceCollection
  (find-dsn [this dsn]
    (if (fn? dsn)
      dsn
      (get dsns (or dsn default-dsn))))

  (get-dsn [this dsn]
    (or (find-dsn this dsn)
        (throw (ex-info (if dsn
                          (str "Unknown data source name: " dsn)
                          "No default data source")
                        {:dsns (keys dsns) :default default-dsn})))))
