;; copyright (c) 2015 Sean Corfield

(ns engine.data.jdbc
  "Optional integration with clojure.java.jdbc"
  (:require [clojure.java.jdbc :as sql]
            [engine.committable :as c]
            [engine.queryable :as q]))

(defrecord JDBCDataStore [db-spec pk-map default-pk]

  q/Queryable
  (query [this args]
    (apply sql/query db-spec args))

  c/Committable
  (insert! [this table row]
    (-> (sql/insert! db-spec table row)
        ;; assume result set of generated keys
        ;; and we want the value of the first one
        first vals first))

  (update! [this table row pk v]
    (sql/update! db-spec table row [(str (name pk) " = ?") v]))

  (primary-key [this table]
    (if pk-map
      (get pk-map table default-pk)
      default-pk)))

(defn jdbc-data-source

  ([db-spec]
   (jdbc-data-source db-spec {} :id))

  ([db-spec pk-or-map]
   (if (map? pk-or-map)
     (jdbc-data-source db-spec pk-or-map :id)
     (jdbc-data-source db-spec nil pk-or-map)))

  ([db-spec pk-map default-pk]
   (->JDBCDataStore db-spec pk-map default-pk)))
