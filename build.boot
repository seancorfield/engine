(def version "0.1.0-SNAPSHOT")

(set-env! :resource-paths #{"src"}
          :dependencies   '[[org.clojure/clojure "1.7.0" :scope "provided"]
                            [org.clojure/java.jdbc "0.4.2" :scope "test"]
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
  [g gpg-sign bool "Sign jar using GPG private key."]
  (comp (pom) (jar) (apply push (mapcat identity *opts*))))
