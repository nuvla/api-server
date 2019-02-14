(def +version+ "0.0.1-SNAPSHOT")

(defproject sixsq.nuvla.server/cimi-jar "0.0.1-SNAPSHOT"

  :description "core cimi server"

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

  :pom-location "target/"

  :dependencies
  [[org.clojure/clojure]
   [aleph "0.4.4"]
   [cheshire]                                               ;; newer version needed for ring-json
   [compojure]
   [clj-stacktrace]
   [clj-time]
   [com.amazonaws/aws-java-sdk-s3]
   [com.taoensso/timbre]
   [environ]
   [expound]
   [instaparse]
   [log4j]
   [metosin/spec-tools]
   [me.raynes/fs]
   [org.apache.logging.log4j/log4j-core]
   [org.apache.logging.log4j/log4j-api]
   [org.clojure/data.json]
   [org.clojure/java.classpath]
   [org.clojure/tools.cli]
   [org.clojure/tools.logging]
   [org.clojure/tools.namespace]
   [potemkin]
   [ring/ring-core]
   [ring/ring-json]
   [zookeeper-clj]
   [com.draines/postal]

   ; dependencies for auth
   [buddy/buddy-core]
   [buddy/buddy-hashers]
   [buddy/buddy-sign]
   [clj-http]
   [peridot]
   [sixsq.nuvla.server/utils ~+version+]
   [sixsq.nuvla.server/db-binding-jar ~+version+]
   [sixsq.nuvla.server/token ~+version+]]

  :aot [sixsq.nuvla.server.app.main]

  :profiles
  {
   :provided {:dependencies [[sixsq.nuvla.ring/code ~+version+]]}
   :test     {:dependencies   [[peridot]
                               [org.clojure/test.check]
                               [org.slf4j/slf4j-log4j12]
                               [com.cemerick/url]
                               [org.apache.curator/curator-test]
                               [sixsq.nuvla.server/db-testing-jar ~+version+]
                               [sixsq.nuvla.server/cimi-test-jar ~+version+]]
              :resource-paths ["test-resources"]
              :env            {:config-name      "config-params.edn"
                               :auth-private-key "test-resources/auth_privkey.pem"
                               :auth-public-key  "test-resources/auth_pubkey.pem"}
              :aot            :all}
   :dev      {:resource-paths ["test-resources"]}
   })
