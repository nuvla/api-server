(def +version+ "3.69-SNAPSHOT")

(defproject com.sixsq.slipstream/SlipStreamToolsCli-jar "3.69-SNAPSHOT"

  :distribution "command line utilities"
  :url "https://github.com/slipstream/SlipStreamServer"

  :license {:name "Apache 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.txt"
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

  :aot [com.sixsq.slipstream.tools.cli.ssconfig
        com.sixsq.slipstream.tools.cli.ssconfigdump
        com.sixsq.slipstream.tools.cli.ssconfigmigrate]

  :dependencies
  [[org.clojure/clojure :scope "compile"]
   [org.clojure/data.xml]
   [org.clojure/tools.cli]

   [com.sixsq.slipstream/SlipStreamPersistence ~+version+ :scope "compile"]
   [com.sixsq.slipstream/SlipStreamClojureAPI-cimi ~+version+ :scope "compile"]
   [com.sixsq.slipstream/SlipStreamCljResources-jar ~+version+ :scope "compile"]
   [com.sixsq.slipstream/SlipStreamDbBinding-jar ~+version+ :scope "compile"]

   [com.sixsq.slipstream/SlipStreamDbSerializers-jar ~+version+ :scope "compile"]

   [clj-http]]

  :profiles {:test {:dependencies [[com.sixsq.slipstream/SlipStreamDbTesting-jar ~+version+]]
                    :aot          :all}})
