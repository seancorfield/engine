;; copyright (c) 2015 Sean Corfield

(ns engine.core
  (:require [engine.flow :as f]
            [engine.input :as i]))

(defn engine
  ([dsns] (engine dsns nil))
  ([dsns default-dsn]
   (f/->Engine (i/data-sources dsns default-dsn)
               nil [] nil [])))

(comment
  Engine

  DataSource
  - map of dsn to db-spec
  - default dsn
  (query ds dsn ["sql" args])
  ;; if dsn is nil, use default
  (db-spec ds dsn)
  ;; return db-spec or nil if no match
  ;; dsn can be omitted or nil to get default

  (data-source db-spec)
  ;; (data-source {:default db-spec} :default)
  (data-source dsn-db-spec-map default-dsn)
  ;; default-dsn nil means no default

  (engine ds)
  ;; build engine from DataSource
  ;; result nil, updates []

  (query engine dsn ["sql" args])
  ;; dsn can be omitted
  (commit engine)
  ;; applies all updates and returns result
  (return engine value)
  (transform engine f args)
  ;; set/update result
  (update engine key dsn table row)
  ;; key/dsn can both be omitted
  ;; key creates binding
  ;; need optional PK and PK-gen

  Updates
  - [key dsn table row pk pk-gen]
  - applied in order
  - if key add binding of PK to env
  - if any value in row is keyword, lookup in env
  - if any value in row is map, apply as update recursively first

  Failure
  - need a way to short circuit execution on failure
  - return exception that commit will throw instead
  - may want preserve updates so far or throw away
  - need a way to add updates post-failure

  (fail engine exception)
  ;; wipes updates
  (commit-and-fail engine exception)
  ;; keeps updates
  (failed? engine)
  ;; is this needed in monadic process?
  (update-on-failure ...)
  ;; appends to separate update list
  (recover engine ExceptionClass f)
  ;; if engine is in failure mode with instance of exception class, wipe failure and post updates and call f on new engine and the matched exception
)
