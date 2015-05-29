;; copyright (c) 2015 Sean Corfield

(ns engine.core
  "Main API for workflow engine library."
  (:require [engine.committable :as c]
            [engine.input :as i]
            [engine.queryable :as q]))

(defprotocol EngineFlow
  "Main flow operations in Engine."
  (-query [this args])
  (commit! [this])
  (return [this value])
  (-transform [this f args])
  (-condf [this pred-fn true-fn false-fn fail-fn])
  (-update [this key dsn table row pk key-gen])
  (-delete [this dsn table pk keys])
  (fail [this ex])
  (commit-and-fail [this ex])
  (-update-on-failure [this key dsn table row pk key-gen])
  (-delete-on-failure [this dsn table pk keys])
  (recover [this ex-class f]))

(defrecord Engine [ds result updates failure fail-updates]
  EngineFlow
  ;; querying can always be done regardless of state
  (-query [this args]
    (if (and (keyword? (first args))
             (< 1 (count args)))
      (q/query (i/lookup-dsn ds (first args)) (rest args))
      (q/query (i/lookup-dsn ds nil) args)))
  ;; commit! can always be done regardless of state
  (commit! [this]
    (if failure
      (do
        (c/commit! ds fail-updates)
        (throw failure))
      (do
        (c/commit! ds updates)
        result)))
  ;; happy path workflow
  (return [this value]
    (if failure this (assoc this :result value)))
  (-transform [this f args]
    (if failure this (apply update-in this [:result] f args)))
  (-condf [this pred-fn true-fn false-fn fail-fn]
    (cond (and failure
               fail-fn)    (fail-fn this)
          failure          this
          (pred-dn result) (true-fn this)
          false-fn         (false-fn this)
          :else            this))
  (-update [this key dsn table row pk key-gen]
    (if failure this
        (update-in this [:updates]
                   conj [(and key (keyword (name key)))
                         (and (i/lookup-dsn ds dsn) dsn)
                         table row pk key-gen])))
  (-delete [this dsn table pk keys]
    (if failure this
        (update-in this [:updates]
                   conj [nil
                         (and (i/lookup-dsn ds dsn) dsn)
                         table nil pk nil keys])))
  ;; sad path workflow
  (fail [this ex]
    ;; retain original failure
    (if failure this (assoc this :failure ex :updates [])))
  (commit-and-fail [this ex]
    ;; retain original failure
    (if failure this (assoc this :failure ex)))
  (-update-on-failure [this key dsn table row pk key-gen]
    ;; actions if anything fails
    (update-in this [:fail-updates]
               conj [(and key (keyword (name key)))
                     (and (i/lookup-dsn ds dsn) dsn)
                     table row pk key-gen]))
  (-delete-on-failure [this dsn table pk keys]
    (update-in this [:fail-updates]
               conj [nil
                     (and (i/lookup-dsn ds dsn) dsn)
                     table nil pk nil keys]))
  (recover [this ex-class f]
    ;; perform recovery if failed in that way
    (if (and failure (instance? ex-class failure))
      (f (assoc this :failure nil :fail-updates []))
      this)))

;; variadic helper functions

(defn query [this & args] (-query this args))

(defn transform [this f & args] (-transform this f args))

(defn condf
  ([this pred-fn true-fn] (-condf pred-fn true-fn nil nil))
  ([this pred-fn true-fn false-fn] (-condf pred-fn true-fn false-fn nil))
  ([this pred-fn true-fn false-fn fail-fn] (-condf pred-fn true-fn false-fn fail-fn)))

(defn update
  ([this table row] (-update this nil nil table row nil nil))
  ([this dsn table row] (-update this nil dsn table row nil nil))
  ([this key dsn table row] (-update this key dsn table row nil nil))
  ([this key dsn table row pk] (-update this key dsn table row pk nil))
  ([this key dsn table row pk key-gen] (-update this key dsn table row pk key-gen)))

(defn delete
  ([this keys] (-delete this nil nil nil keys))
  ([this table keys] (-delete this nil table nil keys))
  ([this dsn table keys] (-delete this dsn table nil keys))
  ([this dsn table pk keys] (-delete this dsn table pk keys)))

(defn update-on-failure
  ([this table row] (-update-on-failure this nil nil table row nil nil))
  ([this dsn table row] (-update-on-failure this nil dsn table row nil nil))
  ([this key dsn table row] (-update-on-failure this key dsn table row nil nil))
  ([this key dsn table row pk] (-update-on-failure this key dsn table row pk nil))
  ([this key dsn table row pk key-gen] (-update-on-failure this key dsn table row pk key-gen)))

(defn delete-on-failure
  ([this table] (-delete-on-failure this nil table nil nil))
  ([this table keys] (-delete-on-failure this nil table nil keys))
  ([this dsn table keys] (-delete-on-failure this dsn table nil keys))
  ([this dsn table pk keys] (-delete-on-failure this dsn table pk keys)))

;; main API function

(defn engine
  "Given a map of data sources and optionally a default data source,
  return a workflow engine, ready to do some business!"
  ([dsns] (engine dsns nil))
  ([dsns default-dsn]
   (->Engine (i/data-sources dsns default-dsn)
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
