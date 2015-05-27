(ns engine.flow
  (:require [engine.committable :as c]
            [engine.input :as i]))

(defprotocol EngineFlow
  "Main flow operations in Engine."
  (-query [this args])
  (commit! [this])
  (return [this value])
  (-transform [this f args])
  (-update [this key dsn table row pk key-gen])
  (fail [this ex])
  (commit-and-fail [this ex])
  (-update-on-failure [this key dsn table row pk key-gen])
  (recover [this ex-class f]))

(defn query [this & args] (-query this args))

(defn transform [this f & args] (-transform this f args))

(defn update
  ([this table row] (-update this nil nil table row nil identity))
  ([this dsn table row] (-update this nil dsn table row nil identity))
  ([this key dsn table row] (-update this key dsn table row nil identity))
  ([this key dsn table row pk] (-update this key dsn table row pk identity))
  ([this key dsn table row pk key-gen] (-update this key dsn table row pk key-gen)))

(defn update-on-failure
  ([this table row] (-update-on-failure this nil nil table row nil identity))
  ([this dsn table row] (-update-on-failure this nil dsn table row nil identity))
  ([this key dsn table row] (-update-on-failure this key dsn table row nil identity))
  ([this key dsn table row pk] (-update-on-failure this key dsn table row pk identity))
  ([this key dsn table row pk key-gen] (-update-on-failure this key dsn table row pk key-gen)))

(defrecord Engine [ds result updates failure fail-updates]
  EngineFlow
  ;; querying can always be done regardless of state
  (-query [this args]
    (if (and (keyword? (first args))
             (< 1 (count args)))
      (apply i/query ds (first args) (rest args))
      (apply i/query ds nil args)))
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
  (-update [this key dsn table row pk key-gen]
    (if failure this
        (update-in this [:updates]
                   conj [key (and (i/queryable ds dsn) dsn) table row pk key-gen])))
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
               conj [key (and (i/queryable ds dsn) dsn) table row pk key-gen]))
  (recover [this ex-class f]
    ;; perform recovery if failed in that way
    (if (and failure (instance? ex-class failure))
      (f (assoc this :failure nil :fail-updates []))
      this)))

(defn engine
  ([dsns] (engine dsns nil))
  ([dsns default-dsn]
   (->Engine (i/data-sources dsns default-dsn)
             nil [] nil [])))
