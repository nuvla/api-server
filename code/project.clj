(def parent-version "6.7.14")
(def nuvla-ring-version "2.0.8")
(def kinsky-version "0.3.1")

(defproject sixsq.nuvla.server/api-jar "6.4.3-SNAPSHOT"

  :description "core api server"

  :url "https://github.com/nuvla/server"

  :license {:name         "Apache 2.0"
            :url          "http://www.apache.org/licenses/LICENSE-2.0.txt"
            :distribution :repo}

  :plugins [[lein-parent "0.3.9"]
            [lein-environ "1.2.0"]
            [lein-project-version "0.1.0"]]

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
  [[metosin/tilakone "0.0.4"]
   [buddy/buddy-core]
   [org.clojars.konstan/kinsky ~kinsky-version]
   [buddy/buddy-hashers]
   [buddy/buddy-sign]
   ;; TODO: remove explicit version once parent is updated.
   [cc.qbits/spandex "0.8.2" :exclusions [org.clojure/clojure]]
   [compojure]
   [com.draines/postal]
   [clj-http]
   [clj-stacktrace]
   [clojure.java-time]
   [com.amazonaws/aws-java-sdk-s3]
   [duratom :exclusions [org.clojure/clojure]]
   [expound]
   [instaparse]
   [metosin/spec-tools]
   [org.bouncycastle/bcpkix-jdk15on "1.70"]
   [selmer "1.12.50"]
   [org.clojure/data.json]
   [org.clojure/java.classpath]
   [org.clojure/tools.namespace]
   [org.clojure/tools.reader]                               ;; required by spandex through core.async
   [ring/ring-core]
   [ring/ring-json]
   [zookeeper-clj :exclusions [[org.slf4j/slf4j-log4j12]]]
   [factual/geo "3.0.1" :exclusions [[org.locationtech.jts/jts-core]
                                     [org.locationtech.spatial4j/spatial4j]
                                     [org.wololo/jts2geojson]]]
   ;; spatial4j and jts-core are needed for factual/geo and for elasticsearch mock test instance
   ;; issue in Factual geo https://github.com/Factual/geo/issues/74
   [org.locationtech.spatial4j/spatial4j "0.8"]
   [org.locationtech.jts/jts-core "1.18.2"]
   ;; need for Factual geo wkt polygon to geojson
   ;; upgrading jts2geojson dependency to 0.16 or 0.17 creates conflicts for now
   [org.wololo/jts2geojson "0.15.0"]
   [one-time "0.7.0"]]

  :profiles
  {
   :provided {:dependencies [[org.clojure/clojure]
                             [sixsq.nuvla.ring/code ~nuvla-ring-version]
                             [org.clojars.konstan/kinsky-test-jar ~kinsky-version]]}

   :test     {:dependencies   [[org.ow2.asm/asm "9.5"]
                               [me.raynes/fs]
                               [peridot]
                               [clj-test-containers "0.7.4"]
                               [org.apache.logging.log4j/log4j-core] ;; needed for ES logging
                               [org.apache.logging.log4j/log4j-api] ;; needed for ES logging
                               [org.clojure/test.check]
                               [org.slf4j/slf4j-api]
                               [org.slf4j/slf4j-log4j12]
                               [com.cemerick/url]
                               [org.apache.curator/curator-test]
                               [org.clojars.konstan/kinsky-test-jar ~kinsky-version]]
              :resource-paths ["test-resources"]
              :env            {:nuvla-session-key           "test-resources/session.key"
                               :nuvla-session-crt           "test-resources/session.crt"
                               :es-sniffer-init             "no"
                               :kafka-producer-init         "yes"}
              :aot            :all
              :plugins        [[org.clojars.konstan/lein-test-report-sonar "0.0.4"]]
              :test-report-sonar {:output-dir "test-reports"
                                  :emit-junit-xml true}}

   :dev      {:dependencies          [[org.apache.zookeeper/zookeeper]
                                      [clj-kondo "RELEASE"]
                                      ;; for running linters
                                      [me.raynes/fs]
                                      [peridot]
                                      [org.apache.curator/curator-test]
                                      [org.apache.logging.log4j/log4j-core]]
              ;; paths
              :source-paths          ["src"]
              :test-paths            ["test"]
              :resource-paths        ["test-resources"]
              ;; linters
              :eastwood              {:exclude-namespaces [sixsq.nuvla.server.resources.job.utils]}
              :env                   {:nuvla-session-key   "test-resources/session.key"
                                      :nuvla-session-crt   "test-resources/session.crt"
                                      :es-sniffer-init     "no"
                                      :kafka-producer-init "no"}
              ;; code coverage
              :cloverage             {:ns-exclude-regex [#"sixsq.nuvla.pricing.protocol"]}
              }})
