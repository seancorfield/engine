(defproject engine "0.1.0-SNAPSHOT"
  :description "A workflow engine that implements query -> logic -> updates."
  :url "https://github.com/seancorfield/engine"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]]
  :plugins [[cider/cider-nrepl "0.8.1"]]
  :profiles {:dev {:dependencies [[org.clojure/java.jdbc "0.3.7"]
                                  [mysql/mysql-connector-java "5.1.28"]]}})
