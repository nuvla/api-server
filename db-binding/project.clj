(def +version+ "0.0.1-SNAPSHOT")

(defproject sixsq.nuvla.server/db-binding-jar "0.0.1-SNAPSHOT"

  :description "bindings for (persistent) database backends"

  :url "https://github.com/nuvla/server"

  :license {:name         "Apache 2.0"
            :url          "http://www.apache.org/licenses/LICENSE-2.0.txt"
            :distribution :repo}

  :plugins [[lein-parent "0.3.5"]]

  :parent-project {:coords  [sixsq.nuvla/parent "6.2.0"]
                   :inherit [:plugins
                             :min-lein-version
                             :managed-dependencies
                             :repositories
                             :deploy-repositories]}

  :source-paths ["src"]

  :resource-paths ["resources"]

  :test-paths ["test"]

  :pom-location "target/"

  :dependencies
  [[cc.qbits/spandex :exclusions [org.clojure/clojure]]
   [clj-time]
   [duratom :exclusions [org.clojure/clojure]]
   [instaparse]
   [metosin/spec-tools]
   [org.clojure/tools.reader]                               ;; required by spandex through core.async

   ;; internal dependencies
   [sixsq.nuvla.server/utils ~+version+]]

  :profiles {:test     {:aot            :all
                        :resource-paths ["test-resources"]
                        :dependencies   [[org.slf4j/slf4j-api]
                                         [org.slf4j/slf4j-log4j12]
                                         [me.raynes/fs]
                                         [org.elasticsearch.client/transport]
                                         [org.elasticsearch.test/framework]
                                         [org.apache.logging.log4j/log4j-core]]}
             :provided {:dependencies [[org.clojure/clojure]
                                       [environ]
                                       [org.clojure/tools.logging]]}})
