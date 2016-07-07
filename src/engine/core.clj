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
  (do-commit! [this]
    "Commit updates/deletes and yield nil or the failure (if any).")
  (return [this value]
    "Set/replace the result this engine will return on a commit!")
  (state [this]
    "Yield the current state -- the result that would be returned.
    If the engine is in failure mode, return nil.")
  (-transform [this f args]
    "Transform the result this engine will return on a commit!
    This is a pure transform, based on just the result value.")
  (-ifp [this pred-fn true-fn false-fn fail-fn]
    "If the pred-fn is true of the current state, call true-fn on the
    engine, else call false-fn on the engine. If the engine was
    in failure mode, call fail-fn on it instead. Both false-fn
    and fail-fn can be nil, indicating no action should be taken.")
  (-ifq [this query-fn true-fn false-fn fail-fn]
    "If the query-fn is true of the engine, call true-fn on the
    engine, else call false-fn on the engine. If the engine was
    in failure mode, call fail-fn on it instead. Both false-fn
    and fail-fn can be nil, indicating no action should be taken.")
  (-update [this key dsn table row pk key-gen]
    "Add this update to be applied on a successful commit!")
  (-delete [this dsn table pk keys]
    "Add this delete to be applied on a successful commit!")
  (fail [this ex]
    "Switch the engine into failure mode, with the given expression.
    Any pending updates/deletes are discarded.")
  (commit-and-fail [this ex]
    "Switch the engine into failure mode, with the given expression.
    Any pending updates/deletes will still be committed.")
  (-update-on-failure [this key dsn table row pk key-gen]
    "Add this update to be applied on a failed commit!")
  (-delete-on-failure [this dsn table pk keys]
    "Add this update to be applied on a failed commit!")
  (recover [this ex-class f]
    "If the engine has failed with an instance of the specified
    class (normally an exception, but can be any expression type),
    switch the engine into success mode, and then apply the given
    function to the former failure and the recovered engine."))

(defrecord Engine [ds result updates failure fail-updates]
  EngineFlow
  ;; querying can always be done regardless of state
  (-query [this args]
    (if (and (keyword? (first args)) (< 1 (count args)))
      (if-let [dsn (i/find-dsn ds (first args))]
        (q/query dsn (rest args))
        (q/query (i/get-dsn ds nil) args))
      (q/query (i/get-dsn ds nil) args)))
  ;; commit! can always be done regardless of state
  (commit! [this]
    (c/commit! ds (if failure fail-updates updates))
    (if failure
      (throw failure)
      result))
  (do-commit! [this]
    (c/commit! ds (if failure fail-updates updates))
    (when failure
      failure))
  ;; happy path workflow
  (return [this value]
    (if failure this (assoc this :result value)))
  (state [this]
    (when-not failure result))
  (-transform [this f args]
    (if failure this (apply update-in this [:result] f args)))
  (-ifp [this pred-fn true-fn false-fn fail-fn]
    (cond (and failure
               fail-fn)    (fail-fn this)
          failure          this
          (pred-fn result) (true-fn this)
          false-fn         (false-fn this)
          :else            this))
  (-ifq [this query-fn true-fn false-fn fail-fn]
    (cond (and failure
               fail-fn)    (fail-fn this)
          failure          this
          (query-fn this)  (true-fn this)
          false-fn         (false-fn this)
          :else            this))
  (-update [this key dsn table row pk key-gen]
    (if failure this
        (update-in this [:updates]
                   conj [(and key (keyword (name key)))
                         (and (i/get-dsn ds dsn) dsn)
                         table row pk key-gen])))
  (-delete [this dsn table pk keys]
    (if failure this
        (update-in this [:updates]
                   conj [nil
                         (and (i/get-dsn ds dsn) dsn)
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
                     (and (i/get-dsn ds dsn) dsn)
                     table row pk key-gen]))
  (-delete-on-failure [this dsn table pk keys]
    (update-in this [:fail-updates]
               conj [nil
                     (and (i/get-dsn ds dsn) dsn)
                     table nil pk nil keys]))
  (recover [this ex-class f]
    ;; perform recovery if failed in that way
    (if (and failure (instance? ex-class failure))
      (f (assoc this :failure nil :fail-updates []) failure)
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

(defn ifp
  "Provide defaults for false-fn and fail-fn."
  ([this pred-fn true-fn] (-ifp this pred-fn true-fn nil nil))
  ([this pred-fn true-fn false-fn] (-ifp this pred-fn true-fn false-fn nil))
  ([this pred-fn true-fn false-fn fail-fn] (-ifp this pred-fn true-fn false-fn fail-fn)))

(defn ifq
  "Provide defaults for false-fn and fail-fn."
  ([this query-fn true-fn] (-ifq this query-fn true-fn nil nil))
  ([this query-fn true-fn false-fn] (-ifq this query-fn true-fn false-fn nil))
  ([this query-fn true-fn false-fn fail-fn] (-ifq this query-fn true-fn false-fn fail-fn)))

(defmacro ifp->
  "Threaded version of ifp."
  ([this pred-x true-x]
   `(ifp ~this #(-> % ~pred-x) #(-> % ~true-x)))
  ([this pred-x true-x false-x]
   `(ifp ~this #(-> % ~pred-x) #(-> % ~true-x) #(-> % ~false-x)))
  ([this pred-x true-x false-x fail-x]
   `(ifp ~this #(-> % ~pred-x) #(-> % ~true-x) #(-> % ~false-x) #(-> % ~fail-x))))

(defmacro ifq->
  "Threaded version of ifq."
  ([this query-x true-x]
   `(ifq ~this #(-> % ~query-x) #(-> % ~true-x)))
  ([this query-x true-x false-x]
   `(ifq ~this #(-> % ~query-x) #(-> % ~true-x) #(-> % ~false-x)))
  ([this query-x true-x false-x fail-x]
   `(ifq ~this #(-> % ~query-x) #(-> % ~true-x) #(-> % ~false-x) #(-> % ~fail-x))))

(defmacro condp->
  "Cascading ifp-> with pred-x true-x pairs."
  [this pred-x true-x & more]
  (if (seq more)
    `(ifp ~this #(-> % ~pred-x) #(-> % ~true-x) #(-> % (condp-> ~@more)))
    `(ifp ~this #(-> % ~pred-x) #(-> % ~true-x))))

(defmacro condq->
  "Cascading ifq-> with query-x true-x pairs."
  [this query-x true-x & more]
  (if (seq more)
    `(ifq ~this #(-> % ~query-x) #(-> % ~true-x) #(-> % (condq-> ~@more)))
    `(ifq ~this #(-> % ~query-x) #(-> % ~true-x))))

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
