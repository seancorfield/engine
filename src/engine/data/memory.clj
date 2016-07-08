;; copyright (c) 2015-2016 Sean Corfield

(ns engine.data.memory
  "Simple in-memory data stores.

  Querying: (e/query app :path :to :data)
  Updating: (e/update app [:path :to :data] new-value)
  Shortcut: (e/update app :key new-value)
  Deleting: (e/delete app :key)

  InMemoryDataStore is Queryable and Committable
  HashMap is just Queryable"
  (:refer-clojure :exclude [hash-map])
  (:require [engine.committable :as c]
            [engine.queryable :as q]))

(defrecord InMemoryDataStore [data]

  q/Queryable
  (query [this args]
    ;; args will be key path into the data
    ;; e.g., (query ds :foo :bar :baz)
    (get-in @data args))

  c/Committable
  (delete! [this _ _ key]
    ;; we only support top-level key deletion
    (swap! data dissoc key))

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

  (key-generator [this _] nil)

  (primary-key [this _] nil))

(defn in-memory-data-source
  "Return a Queryable/Committable key-based datasource."
  ([]     (in-memory-data-source {}))
  ([seed] (->InMemoryDataStore (atom seed))))

;; Queryable can apply to pretty much anything that
;; can be navigated via get-in

(extend-protocol q/Queryable

  clojure.lang.ILookup
  (query [this args]
    (get-in this args))

  clojure.lang.IPersistentSet
  (query [this args]
    (get-in this args))

  java.util.Map
  (query [this args]
    (get-in this args)))
