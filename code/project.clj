(def parent-version "6.8.0")
(def nuvla-ring-version "2.3.0")
(def kinsky-version "0.3.1")

(defproject com.sixsq.nuvla/api-server
  ; x-release-please-start-version
  "6.16.2"
  ; x-release-please-end

  :description "core api server"

  :url "https://github.com/nuvla/api-server"

  :license {:name         "Apache 2.0"
            :url          "http://www.apache.org/licenses/LICENSE-2.0.txt"
            :distribution :repo}

  :plugins [[lein-parent "0.3.9"]
            [lein-libdir "0.1.1"]]

  :libdir-path "target/lib"

  :parent-project {:coords  [sixsq.nuvla/parent ~parent-version]
                   :inherit [:plugins
                             :repositories
                             :deploy-repositories]}

  :source-paths ["src"]

  :resource-paths ["resources"]

  :pom-location "target/"

  :dependencies
  [[cc.qbits/spandex "0.8.2" :exclusions [[ring/ring-codec]]]
   [metosin/tilakone "0.0.4"]
   [zookeeper-clj "0.13.0"
    :exclusions [[potemkin]
                 [commons-io]
                 [io.netty/netty-handler]
                 [io.netty/netty-transport-native-epoll]]]
   [org.clojars.konstan/kinsky ~kinsky-version
    :exclusions [com.github.luben/zstd-jni]]
   [compojure "1.7.1" :exclusions [[ring/ring-core]]]
   [instaparse "1.5.0"]
   [metosin/spec-tools "0.10.7"]
   [expound "0.9.0"]
   [com.draines/postal "2.0.5"]
   [org.clojure/data.csv "1.1.0"]
   ;; com.fasterxml.jackson is conflicting with org.wololo/jts2geojson "0.18.1"
   [metosin/jsonista "0.3.13" :exclusions [[com.fasterxml.jackson.core/jackson-core]
                                           [com.fasterxml.jackson.core/jackson-databind]]]
   [ring/ring-json "0.5.1" :exclusions [[ring/ring-core]]]
   [ring-middleware-accept "2.0.3"]
   [buddy/buddy-core "1.12.0-430"]
   [buddy/buddy-hashers "2.0.167"]
   [buddy/buddy-sign "3.6.1-359"]
   [clj-http "3.13.0" :exclusions [[org.apache.httpcomponents/httpasyncclient]
                                   [potemkin]
                                   [commons-io]]]
   [clj-stacktrace "0.2.8"]
   [clojure.java-time "1.4.3"]
   [com.amazonaws/aws-java-sdk-s3 "1.12.780"]
   [duratom "0.5.9"]
   [org.bouncycastle/bcpkix-jdk15on "1.70"]
   [selmer "1.12.62"]
   [org.clojure/java.classpath "1.1.0"]
   [org.clojure/tools.namespace "1.5.0"]
   [factual/geo "3.0.1" :exclusions [[org.locationtech.jts/jts-core]
                                     [org.locationtech.spatial4j/spatial4j]
                                     [org.wololo/jts2geojson]
                                     [junit/junit]]]
   ;; spatial4j and jts-core are needed for factual/geo
   ;; issue in Factual geo https://github.com/Factual/geo/issues/74
   [org.locationtech.spatial4j/spatial4j "0.8"]
   [org.locationtech.jts/jts-core "1.20.0"]
   ;; need for Factual geo wkt polygon to geojson
   ;; upgrading jts2geojson dependency to 0.16 or 0.17 creates conflicts for now
   [org.wololo/jts2geojson "0.18.1"]
   [one-time "0.8.0" :exclusions [[org.apache.xmlgraphics/batik-dom]
                                  [org.apache.xmlgraphics/batik-svggen]
                                  [com.github.kenglxn.qrgen/javase]
                                  [com.google.zxing/javase]]]
   [funcool/promesa "11.0.678"]
   [nrepl "1.3.1"]
   [com.github.java-json-tools/json-patch "1.13"]
   [io.forward/semver "0.1.0"]
   [org.clojure/tools.reader "1.5.0"]
   [com.taoensso/telemere "1.0.0-RC2"]
   [com.taoensso/telemere-slf4j "1.0.0-RC2"]]

  :profiles
  {:provided {:dependencies [[org.clojure/clojure "1.12.0"]
                             [com.sixsq.nuvla/ring ~nuvla-ring-version]
                             [org.clojars.konstan/kinsky-test-jar ~kinsky-version]]}

   :test     {:dependencies      [[me.raynes/fs "1.4.6"]
                                  [org.testcontainers/testcontainers "1.20.4"]
                                  [peridot "0.5.4"]
                                  [clj-test-containers "0.7.4"]
                                  [org.clojure/test.check "1.1.1"]
                                  [com.cemerick/url "0.1.1"]
                                  [org.clojars.konstan/kinsky-test-jar ~kinsky-version]
                                  [same/ish "0.1.6"]]
              :resource-paths    ["test-resources"]
              :env               {:nuvla-session-key   "test-resources/session.key"
                                  :nuvla-session-crt   "test-resources/session.crt"
                                  :es-sniffer-init     "no"
                                  :kafka-producer-init "yes"}
              :aot               :all
              :plugins           [[org.clojars.konstan/lein-test-report-sonar "0.0.4"]]
              :test-report-sonar {:output-dir     "test-reports"
                                  :emit-junit-xml true}}

   :dev       {:dependencies   [[clj-kondo "RELEASE"]]
               ;; paths
               :source-paths   ["src"]
               :test-paths     ["test"]
               :resource-paths ["test-resources"]
               ;; linters
               :eastwood       {:exclude-namespaces [com.sixsq.nuvla.server.resources.job.utils]}
               :env            {:nuvla-session-key   "test-resources/session.key"
                                :nuvla-session-crt   "test-resources/session.crt"
                                :es-sniffer-init     "no"
                                :kafka-producer-init "no"}
               ;; code coverage
               :cloverage      {:ns-exclude-regex [#"com.sixsq.nuvla.pricing.protocol"]}
               }
   :migration {:source-paths ["migration-scripts/src"]}})
