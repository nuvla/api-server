(def +version+ "0.0.1-SNAPSHOT")

(defproject sixsq.nuvla.server/db-testing-jar "0.0.1-SNAPSHOT"

  :description "db testing utilities"

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

  :source-paths ["src"]

  :resource-paths ["resources"]

  :test-paths ["test"]

  :java-source-paths ["java"]

  :pom-location "target/"

  :aot :all

  :dependencies
  [[org.clojure/clojure]

   [environ]

   ; FIXME: needed this one after requiring
   ; sixsq.nuvla.ssclj.middleware.authn-info-header
   [cheshire]
   [cc.qbits/spandex]
   [org.clojure/data.xml]
   [clj-time]
   [me.raynes/fs]
   [org.clojure/data.json]
   [org.clojure/tools.logging]
   [org.clojure/tools.reader]
   [org.elasticsearch/elasticsearch]
   ; required by elasticsearch
   [org.apache.logging.log4j/log4j-core]
   [org.apache.logging.log4j/log4j-api]
   [org.apache.logging.log4j/log4j-web]
   [org.elasticsearch.client/transport]
   [org.elasticsearch.plugin/transport-netty4-client]
   [org.elasticsearch.test/framework]

   [ring/ring-json]

   [sixsq.nuvla.server/db-binding-jar ~+version+]

   ;;
   ;; This dependency is included explicitly to avoid having
   ;; ring/ring-json pull in an old version of ring-core that
   ;; conflicts with the more recent one.
   ;;
   [ring/ring-core]]

  :profiles {:test {:dependencies [[org.slf4j/slf4j-log4j12]]
                    :resource-paths ["test-resources"]}})
