(def +version+ "3.69-SNAPSHOT")

(defproject sixsq.nuvla.server/db-binding-jar "3.69-SNAPSHOT"

  :description "bindings for (persistent) database backends"

  :url "https://github.com/slipstream/SlipStreamServer"

  :license {:name         "Apache 2.0"
            :url          "http://www.apache.org/licenses/LICENSE-2.0.txt"
            :distribution :repo}

  :plugins [[lein-parent "0.3.2"]]

  :parent-project {:coords  [sixsq/slipstream-parent "5.3.19"]
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
   [camel-snake-kebab]
   [cheshire]                                               ;; to avoid transient dependency conflicts
   [clj-time]
   [com.rpl/specter]
   [com.sixsq.slipstream/utils ~+version+]
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
   [superstring]                                            ;; needed for pascal case conversion function
   ]

  :profiles {:test     {:aot            :all
                        :resource-paths ["test-resources"]
                        :dependencies   [[org.slf4j/slf4j-log4j12]
                                         [me.raynes/fs]
                                         [org.elasticsearch.test/framework]]}
             :provided {:dependencies [[org.clojure/clojure]]}})
