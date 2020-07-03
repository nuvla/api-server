(def parent-version "6.7.3")
(def nuvla-ring-version "2.0.2")

(defproject sixsq.nuvla.server/api-jar "4.2.17-SNAPSHOT"

  :description "core api server"

  :url "https://github.com/nuvla/server"

  :license {:name         "Apache 2.0"
            :url          "http://www.apache.org/licenses/LICENSE-2.0.txt"
            :distribution :repo}

  :plugins [[lein-parent "0.3.5"]
            [lein-environ "1.1.0"]]

  :parent-project {:coords  [sixsq.nuvla/parent ~parent-version]
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
   ;; waiting for cc.qbits/spandex 0.7.5 to be release
   ;; ES 7.7 need elasticsearch client version to be bumped
   [org.clojars.0xbase12/spandex "0.7.5-SNAPSHOT" :exclusions [org.clojure/clojure]]
   [compojure]
   [com.draines/postal]
   [clj-commons/clj-yaml]
   [clj-http]
   [clj-stacktrace]
   [clojure.java-time]
   [com.amazonaws/aws-java-sdk-s3]
   [com.stripe/stripe-java "19.24.0"]
   [duratom :exclusions [org.clojure/clojure]]
   [expound]
   [instaparse]
   [metosin/spec-tools]
   [org.bouncycastle/bcpkix-jdk15on "1.62"]
   [org.clojure/data.json]
   [org.clojure/java.classpath]
   [org.clojure/tools.namespace]
   [org.clojure/tools.reader]                               ;; required by spandex through core.async
   [ring/ring-core]
   [ring/ring-json]
   [zookeeper-clj :exclusions [[jline]
                               [org.clojure/clojure]
                               [org.slf4j/slf4j-api]
                               [org.slf4j/slf4j-log4j12]
                               [io.netty/netty]]]
   ]

  :aot [sixsq.nuvla.server.app.main]

  :profiles
  {
   :provided {:dependencies [[org.clojure/clojure]
                             [sixsq.nuvla.ring/code ~nuvla-ring-version]]}


   :test     {:dependencies   [[me.raynes/fs]
                               [peridot]
                               [org.apache.logging.log4j/log4j-core] ;; needed for ES logging
                               [org.apache.logging.log4j/log4j-api] ;; needed for ES logging
                               [org.clojure/test.check]
                               [org.elasticsearch.client/transport]
                               [org.elasticsearch.test/framework]
                               [org.slf4j/slf4j-api]
                               [org.slf4j/slf4j-log4j12]
                               [com.cemerick/url]
                               [org.apache.curator/curator-test]]
              :resource-paths ["test-resources"]
              :env            {:nuvla-session-key "test-resources/session.key"
                               :nuvla-session-crt "test-resources/session.crt"}
              :aot            :all}
   :dev      {:resource-paths ["test-resources"]}
   })
