(def +version+ "0.0.1-SNAPSHOT")

(defproject sixsq.nuvla.server/db-binding-jar "0.0.1-SNAPSHOT"

  :description "bindings for (persistent) database backends"

  :url "https://github.com/nuvla/server"

  :license {:name         "Apache 2.0"
            :url          "http://www.apache.org/licenses/LICENSE-2.0.txt"
            :distribution :repo}

  :plugins [[lein-parent "0.3.5"]]

  :parent-project {:coords  [sixsq.nuvla/parent "6.1.3"]
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
  [[cc.qbits/spandex]
   [cheshire]                                               ;; to avoid transient dependency conflicts
   [clj-time]
   [com.rpl/specter]
   [sixsq.nuvla.server/utils ~+version+]
   [duratom]
   [environ]
   [instaparse]
   [metosin/spec-tools]
   [org.apache.logging.log4j/log4j-core]                    ;; required for Elasticsearch logging
   [org.clojure/data.json]
   [org.clojure/tools.logging]
   [org.clojure/tools.reader]                               ;; required by spandex through core.async
   [org.elasticsearch/elasticsearch]
   [org.elasticsearch.client/transport]
   [org.slf4j/slf4j-api]
   [ring/ring-json]
   ]

  :profiles {:test     {:aot            :all
                        :resource-paths ["test-resources"]
                        :dependencies   [[org.slf4j/slf4j-log4j12]
                                         [me.raynes/fs]
                                         [org.elasticsearch.test/framework]]}
             :provided {:dependencies [[org.clojure/clojure]]}})
