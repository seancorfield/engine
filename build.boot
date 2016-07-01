(def version "0.1.0-alpha1")

(set-env! :resource-paths #{"src"}
          :dependencies   '[[org.clojure/java.jdbc "0.6.1" :scope "test"]
                            [mysql/mysql-connector-java "5.1.36" :scope "test"]])

(task-options!
 pom {:project     'seancorfield/engine
      :version     version
      :description "A workflow engine that implements query -> logic -> updates."
      :url         "https://github.com/seancorfield/engine"
      :scm         {:url "https://github.com/seancorfield/engine"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask build []
  (comp (pom) (jar) (install)))

(deftask deploy
  []
  (comp (pom) (jar) (push)))
