;; copyright (c) 2015 Sean Corfield

(ns engine.queryable
  "Queryable protocol for input data sources.")

(defprotocol Queryable
  "A data source is queryable. Each specific data source
  type should implement this protocol."
  (-query [this args] "Run the query and return the result."))
