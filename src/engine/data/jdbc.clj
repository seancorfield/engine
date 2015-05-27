(ns engine.data.jdbc
  (:require [clojure.java.jdbc :as sql]
            [engine.committable :as c]
            [engine.queryable :as q]))

(defrecord JDBCQueryable [db-spec pk-map]
  q/Queryable
  (-query [this args]
    (apply sql/query db-spec args))
  c/Committable
  (insert! [this table row]
    (-> (sql/insert! db-spec table row)
        first
        :generated_key))
  (update! [this table row pk v]
    (sql/update! db-spec table row [(str (name pk) " = ?") v]))
  (primary-key [this table]
    (if (map? pk-map)
      (get pk-map table :id)
      pk-map)))

(defn jdbc-data-source
  ([db-spec] (jdbc-data-source db-spec :id))
  ([db-spec pk-map] (->JDBCQueryable db-spec pk-map)))
