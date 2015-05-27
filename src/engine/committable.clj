(ns engine.committable
  (:require [engine.input :as i]))

(defprotocol Committable
  "A data sink is committable / updateable."
  (insert! [this table row])
  (update! [this table row pk v])
  (primary-key [this table]))

(defn commit!
  [data-sources updates]
  (reduce (fn [env [key dsn table row pk key-gen]]
            (let [ds (i/queryable data-sources dsn)
                  pk (or pk (primary-key ds table))]
              (if-let [pkv (get row pk)]
                (do
                  (update! ds table (dissoc row pk) pk pkv)
                  (cond-> env key (assoc key pkv)))
                (let [new-row (key-gen row)
                      new-pk (insert! ds table row)]
                  (cond-> env key (assoc key new-pk))))))
          {}
          updates))
