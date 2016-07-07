;; copyright (c) 2016 world singles llc

(ns engine.core-test
  (:refer-clojure :exclude [update])
  (:require [expectations :refer [expect in more more-of]]
            [engine.core :refer :all]
            [engine.data.memory :as m]))

(expect 1 (-> (engine {:data (m/hash-map :a 1 :b 2 :c 3)} :data)
              (query :a)))

(expect 2 (-> (engine {:data (m/hash-map :a 1 :b 2 :c 3)} :data)
              (query :data :b)))

(expect 3 (-> (engine {:data (m/hash-map :a 1 :b 2 :c 3)})
              (query :data :c)))

(expect (more clojure.lang.ExceptionInfo
              (comp (partial re-find #"No default data source")
                    (memfn getMessage))
              (comp (partial = #{:data}) set :dsns ex-data)
              (comp nil? :default ex-data))
        (-> (engine {:data (m/hash-map :a 1 :b 2 :c 3)})
            (query :d)))

(expect true (-> (engine {:data (m/hash-map :a 1 :b 2 :c 3)})
                 (ifq-> (query :data :a)
                        (return true)
                        (fail (ex-info "" {})))
                 (commit!)))

(expect nil? (-> (engine {:data (m/hash-map :a 1 :b 2 :c 3)})
                 (return "not nil!")
                 (ifq-> (query :data :a)
                        (return true)
                        (fail (ex-info "" {})))
                 (do-commit!)))

(expect (more clojure.lang.ExceptionInfo
              (comp (partial = "d is missing") (memfn getMessage))
              (comp (partial = {}) ex-data))
        (-> (engine {:data (m/hash-map :a 1 :b 2 :c 3)})
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
