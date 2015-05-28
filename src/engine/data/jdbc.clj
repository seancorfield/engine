;; copyright (c) 2015 Sean Corfield

(ns engine.data.jdbc
  "Optional integration with clojure.java.jdbc"
  (:require [clojure.string :as str]
            [clojure.java.jdbc :as sql]
            [engine.committable :as c]
            [engine.queryable :as q]))

(defrecord JDBCDataStore [db-spec pk-map default-pk key-gen-map]

  q/Queryable
  (query [this args]
    (apply sql/query db-spec args))

  c/Committable
  (delete! [this table pk v]
    (if (and table pk)
      (if (set? v)
        (let [n (count v)
              ? (str/join "," (repeat n "?"))]
          (sql/delete! db-spec table
                       (apply conj [(str (name pk) " IN (" ? ")")] v)))
        (sql/delete! db-spec table
                     (if (nil? v)
                       [(str (name pk) " IS NULL")]
                       [(str (name pk) " = ?") v])))
      (throw (ex-info "jdbc data store requires table and key for delete!"
                      {:table table :pk pk :v v}))))

  (insert! [this table row]
    (-> (sql/insert! db-spec table row)
        ;; assume result set of generated keys
        ;; and we want the value of the first one
        first vals first))

  (update! [this table row pk v]
    (sql/update! db-spec table row [(str (name pk) " = ?") v]))

  (key-generator [this table]
    (if (map? key-gen-map)
      (get key-gen-map table)
      key-gen-map))

  (primary-key [this table]
    (if pk-map
      (get pk-map table default-pk)
      default-pk)))

(defn jdbc-data-source

  ([db-spec]
   (jdbc-data-source db-spec {} :id nil))

  ([db-spec pk-or-map]
   (if (map? pk-or-map)
     (jdbc-data-source db-spec pk-or-map :id nil)
     (jdbc-data-source db-spec nil pk-or-map nil)))

  ([db-spec pk-map default-pk]
   (jdbc-data-source db-spec pk-map default-pk nil))

  ([db-spec pk-map default-pk key-gen-map]
   (->JDBCDataStore db-spec pk-map default-pk key-gen-map)))
