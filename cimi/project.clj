(def +version+ "0.0.1-SNAPSHOT")

(def nuvla-ring-version "0.0.1-SNAPSHOT")

(defproject sixsq.nuvla.server/cimi-jar "0.0.1-SNAPSHOT"

  :description "core cimi server"

  :url "https://github.com/nuvla/server"

  :license {:name "Apache 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.txt"
            :distribution :repo}

  :plugins [[lein-parent "0.3.5"]
            [lein-environ "1.1.0"]]

  :parent-project {:coords  [sixsq.nuvla/parent "6.2.0"]
                   :inherit [:plugins
                             :min-lein-version
                             :managed-dependencies
                             :repositories
                             :deploy-repositories]}

  :source-paths ["src"]

  :resource-paths ["resources"]

  :pom-location "target/"

  :dependencies
  [[buddy/buddy-core]
   [buddy/buddy-hashers]
   [buddy/buddy-sign]
   [compojure]
   [com.draines/postal]
   [clj-http]
   [clj-stacktrace]
   [clj-time]
   [com.amazonaws/aws-java-sdk-s3]
   [expound]
   [instaparse]
   [metosin/spec-tools]
   [org.clojure/data.json]
   [org.clojure/java.classpath]
   [org.clojure/tools.namespace]
   [ring/ring-core]
   [ring/ring-json]
   [zookeeper-clj :exclusions [[org.clojure/clojure]
                               [org.slf4j/slf4j-api]
                               [org.slf4j/slf4j-log4j12]
                               [io.netty/netty]]]

   ;; internal dependencies
   [sixsq.nuvla.server/db-binding-jar ~+version+]]

  :aot [sixsq.nuvla.server.app.main]

  :profiles
  {
   :provided {:dependencies [[org.clojure/clojure]
                             [sixsq.nuvla.ring/code ~nuvla-ring-version]]}
   :test     {:dependencies   [[peridot]
                               [org.clojure/test.check]
                               [org.slf4j/slf4j-log4j12]
                               [com.cemerick/url]
                               [org.apache.curator/curator-test]
                               [sixsq.nuvla.server/cimi-test-jar ~+version+]]
              :resource-paths ["test-resources"]
              :env            {:auth-private-key "test-resources/auth_privkey.pem"
                               :auth-public-key  "test-resources/auth_pubkey.pem"}
              :aot            :all}
   :dev      {:resource-paths ["test-resources"]}
   })
