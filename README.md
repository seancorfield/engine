# engine

A Clojure library designed to help separate business logic from
persistence by maintaining a strict query -> logic -> updates
workflow across your application.

## Usage

    (require '[engine.core :as e])
    (require '[engine.data.jdbc :as j])
    ;; create a data source for the app
    (def ds (j/jdbc-data-source
      {:dbtype "mysql" :dbname "mydb" :user "me" :password "secret"}))
    ;; create workflow from data source, with default
    (def app (e/engine {:db ds} :db))
    (-> app
        ;; indicate desired updates
        (e/update :user {:id 9 :username "test"})
        (e/update :user {:id 10 :username "ten"})
        ;; indicate intended result
        (e/return 42)
        ;; commit changes
        (e/commit!))
    ;; returns the result and applies the updates

All of the business logic up to the `commit` call is pure.

## License

Copyright © 2015 Sean Corfield

Distributed under the Eclipse Public License version 1.0.
