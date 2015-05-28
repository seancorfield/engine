# engine

A Clojure library designed to help separate business logic from
persistence by maintaining a strict query -> logic -> updates
workflow across your application.

## Usage

    (require '[engine.core :as e])
    (require '[engine.data.jdbc :as j])
    (require '[engine.data.memory :as m])
    ;; create a data source for the app
    (def db (j/jdbc-data-source
      {:dbtype "mysql" :dbname "mydb" :user "me" :password "secret"}))
    (def ram (m/in-memory-data-source))
    ;; create workflow from data sources, with default
    (def app (e/engine {:db db :ram ram} :db))
    (-> app
        ;; indicate desired updates
        (e/update :user {:id 9 :username "nine"})
        (e/update :user {:id 10 :username "ten"})
        (e/delete :user 11)
        (e/update :ram :name "Sean Corfield")
        ;; indicate intended result
        (e/return 42)
        ;; commit changes
        (e/commit!))
    ;; returns the result and applies the updates
    
    (e/query app ["select id,username from user where id < ?" 12])
    ;; returns a result set from the db
    (e/query :ram :name)
    ;; returns the value associated with :name in ram

All of the business logic up to the `commit!` call is pure.

## License

Copyright © 2015 Sean Corfield

Distributed under the Eclipse Public License version 1.0.
