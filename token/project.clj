(def +version+ "0.0.1-SNAPSHOT")

(defproject sixsq.nuvla.server/token "0.0.1-SNAPSHOT"

  :description "token handling utilities in Clojure"

  :url "https://github.com/nuvla/server"

  :license {:name "Apache 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.txt"
            :distribution :repo}

  :plugins [[lein-parent "0.3.2"]]

  :parent-project {:coords  [sixsq.nuvla/parent "6.0.0"]
                   :inherit [:plugins
                             :min-lein-version
                             :managed-dependencies
                             :repositories
                             :deploy-repositories]}

  :pom-location "target/"

  :source-paths ["src"]

  :dependencies
  [[org.clojure/clojure]
   [org.clojure/data.json]
   [buddy/buddy-core]
   [buddy/buddy-hashers]
   [buddy/buddy-sign]
   [environ]
   [log4j]
   [org.clojure/tools.logging]
   [peridot]]

  :profiles {:test {:source-paths   ["test"]
                    :resource-paths ["test-resources"]}
             :dev  {:env {:config-name "config-hsqldb-mem.edn"}}})
