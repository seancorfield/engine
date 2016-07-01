;; copyright (c) 2015-2016 Sean Corfield

(ns engine.core
  "Main API for workflow engine library."
  (:refer-clojure :exclude [update])
  (:require [engine.committable :as c]
            [engine.input :as i]
            [engine.queryable :as q]))

(defprotocol EngineFlow
  "Main flow operations in Engine."
  (-query [this args]
    "Run a query and yield that value.")
  (commit! [this]
    "Commit updates/deletes and yield the returned value.")
  (return [this value]
    "Set/replace the result this engine will return on a commit!")
  (-transform [this f args]
    "Transform the result this engine will return on a commit!
    This is a pure transform, based on just the result value.")
  (-condf [this pred-fn true-fn false-fn fail-fn]
    "If the pred-fn is true of the engine, call true-fn on the
    engine, else call false-fn on the engine. If the engine was
    in failure mode, call fail-fn on it instead. Both false-fn
    and fail-fn can be nil, indicating no action should be taken.")
  (-update [this key dsn table row pk key-gen]
    "Add this update to be applied on a successful commit!")
  (-delete [this dsn table pk keys]
    "Add this delete to be applied on a successful commit!")
  (fail [this ex]
    "Switch the engine into failure mode, with the given exception.
    Any pending updates/deletes are discarded.")
  (commit-and-fail [this ex]
    "Switch the engine into failure mode, with the given exception.
    Any pending updates/deletes will still be committed.")
  (-update-on-failure [this key dsn table row pk key-gen]
    "Add this update to be applied on a failed commit!")
  (-delete-on-failure [this dsn table pk keys]
    "Add this update to be applied on a failed commit!")
  (recover [this ex-class f]
    "If the engine has failed with an instance of the specified
    exception, switch the engine into success mode, and then
    apply the given function to it."))

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
          (pred-fn result) (true-fn this)
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

(defn query
  "Adapter for 2-arity -query."
  [this & args]
  (-query this args))

(defn transform
  "Adapter for 3-arity -transform."
  [this f & args]
  (-transform this f args))

(defn transform->
  "Adapter for 3-arity -transform that threads the engine into
  the transforming function, as the first argument."
  [this f & args]
  (-transform this (partial f this) args))

(defn condf
  "Provide defaults for false-fn and fail-fn."
  ([this pred-fn true-fn] (-condf this pred-fn true-fn nil nil))
  ([this pred-fn true-fn false-fn] (-condf this pred-fn true-fn false-fn nil))
  ([this pred-fn true-fn false-fn fail-fn] (-condf this pred-fn true-fn false-fn fail-fn)))

(defn update
  "Provide defaults for key (label), dsn, pk, and key-gen."
  ([this table row] (-update this nil nil table row nil nil))
  ([this dsn table row] (-update this nil dsn table row nil nil))
  ([this key dsn table row] (-update this key dsn table row nil nil))
  ([this key dsn table row pk] (-update this key dsn table row pk nil))
  ([this key dsn table row pk key-gen] (-update this key dsn table row pk key-gen)))

(defn delete
  "Provide defaults for dsn, table, and pk."
  ([this keys] (-delete this nil nil nil keys))
  ([this table keys] (-delete this nil table nil keys))
  ([this dsn table keys] (-delete this dsn table nil keys))
  ([this dsn table pk keys] (-delete this dsn table pk keys)))

(defn update-on-failure
  "See update above, but applied on failure."
  ([this table row] (-update-on-failure this nil nil table row nil nil))
  ([this dsn table row] (-update-on-failure this nil dsn table row nil nil))
  ([this key dsn table row] (-update-on-failure this key dsn table row nil nil))
  ([this key dsn table row pk] (-update-on-failure this key dsn table row pk nil))
  ([this key dsn table row pk key-gen] (-update-on-failure this key dsn table row pk key-gen)))

(defn delete-on-failure
  "See delete above, but applied on failure."
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
