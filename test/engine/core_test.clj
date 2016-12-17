;; copyright (c) 2016 world singles llc

(ns engine.core-test
  (:refer-clojure :exclude [apply send update])
  (:require [expectations :refer [expect in more more-of]]
            [engine.committable :as c]
            [engine.core :refer :all]
            [engine.data.memory :as m]))

(expect 1 (-> (engine {:data (hash-map :a 1 :b 2 :c 3)} :data)
              (query :a)))

(expect 2 (-> (engine {:data (hash-map :a 1 :b 2 :c 3)} :data)
              (query :data :b)))

(expect 3 (-> (engine {:data (hash-map :a 1 :b 2 :c 3)})
              (query :data :c)))

(expect (more-of ex
                 clojure.lang.ExceptionInfo ex
                 (partial re-find #"No default data source")
                 (.getMessage ex)
                 #{:data}
                 (set (:dsns (ex-data ex)))
                 nil?
                 (:default (ex-data ex)))
        (-> (engine {:data (hash-map :a 1 :b 2 :c 3)})
            (query :d)))

(expect true (-> (engine {:data (hash-map :a 1 :b 2 :c 3)})
                 (ifq-> (query :data :a)
                        (return true)
                        (fail (ex-info "" {})))
                 (commit!)))

(expect nil? (-> (engine {:data (hash-map :a 1 :b 2 :c 3)})
                 (return "not nil!")
                 (ifq-> (query :data :a)
                        (return true)
                        (fail (ex-info "" {})))
                 (do-commit!)))

(expect (more-of ex
                 clojure.lang.ExceptionInfo ex
                 "d is missing" (.getMessage ex)
                 {} (ex-data ex))
        (-> (engine {:data (hash-map :a 1 :b 2 :c 3)})
            (ifq-> (query :data :d)
                   (return true)
                   (fail (ex-info "d is missing" {})))
            (do-commit!)))

(expect "acceptable"
        (-> (engine {})
            (return 42)
            (condp-> odd?
                     (return "odd")
                     (< 13)
                     (return "< 13")
                     (= 13)
                     (return "= 13")
                     (return "acceptable"))
            (commit!)))

(expect 42
        (-> (engine {})
            (return 42)
            (condp-> odd?
                     (return "odd")
                     (< 13)
                     (return "< 13")
                     (= 13)
                     (return "= 13"))
            (commit!)))

(expect "> 13"
        (-> (engine {})
            (return 42)
            (condp-> odd?
                     (return "odd")
                     (< 13)
                     (return "< 13")
                     (> 13)
                     (return "> 13"))
            (commit!)))

(expect #"some : output\r?\n"
        (with-out-str
          (-> (engine {:out (c/insertable-store
                             [_ label value]
                             (println label ":" value))}
                      :out)
              (return 42)
              (send "some" "output")
              (commit!))))

(expect #"some : output\r?\n"
        (with-out-str
          (-> (engine {})
              (return 42)
              (send (fn [env]
                      (println "some" ":" "output")))
              (commit!))))

(expect 43
        (-> (engine {})
            (return 42)
            (apply #(transform % inc))
            (commit!)))

(expect 52
        (-> (engine {})
            (return 42)
            (apply (fn [e a b c d]
                     (transform e #(+ % a b c d)))
                   1 2 [3 4])
            (commit!)))

(expect 52
        (-> (engine {})
            (return 42)
            (apply (fn [e a b c d]
                     (transform e #(+ % a b c d)))
                   [1 2 3 4])
            (commit!)))

(expect :ok
        (-> (engine {})
            (return {:a {:b {:c [:bad :ok]}}})
            (as-> e (return e (state e :a :b :c 1)))
            (commit!)))
