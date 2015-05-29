# engine

A Clojure library designed to help separate business logic from
persistence by maintaining a strict query -> logic -> updates
workflow across your application.

The basic premise is that for each "request" or "unit of work", you create an `engine`
instance with a set of named data sources/sinks. You think perform all of your _pure_
business logic on/with the _engine_ and you tell it what updates you want made to
the data stores as you go along, but those are all queued up and only happen when the
_engine's_ work is `commit!`ed at the end of the process.

Your business logic can run queries against the data sources in the _engine_ and those
are assumed to be pure as well since they are intended to be readonly (see caveat below).
The _engine_ operates in two modes: _normal_ mode, which is assumed to generate a result
and a series of updates to perform, and _failure_ mode, which is assumed to generate an
exception and a different series of updates to perform. Failure mode can be `recover`ed
in a similar way to how `try`/`catch` works with exception.

Caveat about readonly data sources: while updates are deferred by the _engine_,
queries are run as requested and will return whatever the current data store's state
reflects. That means that queries are not idempotent: the same `query` function may
return different values on consecutive calls, if something else has modified the state
of the data store. For example, a JDBC data store will return the current state of
the database which can change over time. A trivial data store that would also exhibit
this behavior would be a clock/timer or a random number generator.

Since data stores are given to the _engine_ when it is constructed, for testing you 
could easily pass in a mocked version for easier testing if you needed to do so.

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
    
    (-> app
        ;; write a log record if something fails
        (e/update-on-failure :logtable {:message "We failed!"})
        ...
        ;; later on we may fail
        (e/condf (< value threshold)
          safe-process ;; called on app
          (fn [app] (e/fail (ex-info "Too big!" {:value value}))))
        ;; we can continue here because the operations know
        ;; about normal/failure modes
        ;; this update will only happen in normal mode
        (e/update :sale {:name "Product" :amount value})
        ;; either write to logtable and throw the exception
        ;; or write to sale and return the engine's value
        (e/commit!))

## License

Copyright © 2015 Sean Corfield

Distributed under the Eclipse Public License version 1.0.
