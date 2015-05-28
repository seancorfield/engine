;; copyright (c) 2015 Sean Corfield

(ns engine.data.memory
  "Simple in-memory data store for testing etc.

  Querying: (e/query app :path :to :data)
  Updating: (e/update app [:path :to :data] new-value)
  Shortcut: (e/update app :key new-value)"
  (:require [engine.committable :as c]
            [engine.queryable :as q]))

(defrecord InMemoryDataStore [data]

  q/Queryable
  (query [this args]
    ;; args will be key path into the data
    ;; e.g., (query ds :foo :bar :baz)
    (get-in @data args))

  c/Committable
  (insert! [this key value]
    (if (vector? key)
      (swap! data update-in key (constantly value))
      (swap! data assoc key value))
    ;; in-memory store does not support key lookup since
    ;; all keys are known, not generated, so return arbitrary
    ;; value of nil
    nil)

  (update! [this key value pk v]
    (throw (ex-info "in-memory data store only supports insert!"
                    {:key key :value value :pk pk :v v})))

  (primary-key [this _] nil))

(defn in-memory-data-source [] (->InMemoryDataStore (atom {})))
