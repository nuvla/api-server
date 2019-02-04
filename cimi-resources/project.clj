(def +version+ "3.69-SNAPSHOT")

(defproject com.sixsq.slipstream/SlipStreamServer-cimi-resources "3.69-SNAPSHOT"

  :description "CIMI resources"

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


  :pom-location "target/"

  :jvm-opts ["-Xmx2000m"]

  :dependencies [[com.sixsq.slipstream/SlipStreamCljResources-jar ~+version+]
                 [com.amazonaws/aws-java-sdk-s3]]

  :profiles {:provided {:dependencies [[org.clojure/clojure]]}
             :test     {:dependencies   [[peridot]
                                         [org.clojure/test.check]
                                         [org.slf4j/slf4j-log4j12]
                                         [com.cemerick/url]
                                         [org.apache.curator/curator-test]
                                         [com.sixsq.slipstream/SlipStreamDbTesting-jar ~+version+]
                                         [com.sixsq.slipstream/SlipStreamCljResourcesTests-jar ~+version+]
                                         [aleph "0.4.4"]]
                        :resource-paths ["test-resources"]
                        :env            {:config-name      "config-params.edn"
                                         :auth-private-key "test-resources/auth_privkey.pem"
                                         :auth-public-key  "test-resources/auth_pubkey.pem"}
                        :aot            :all}})
