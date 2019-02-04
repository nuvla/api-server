(def +version+ "3.69-SNAPSHOT")

(defproject com.sixsq.slipstream/SlipStreamCljResourcesTests-jar "3.69-SNAPSHOT"

  :description "cimi server testing utilities"

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

  :pom-location "target/"

  :dependencies [[com.sixsq.slipstream/SlipStreamDbBinding-jar ~+version+]
                 [com.sixsq.slipstream/SlipStreamDbTesting-jar ~+version+ :scope "compile"]
                 [org.apache.curator/curator-test :scope "compile"]
                 [peridot :scope "compile"]
                 [expound :scope "compile"]
                 [org.clojure/data.json]
                 [compojure]
                 [com.cemerick/url]
                 [com.sixsq.slipstream/slipstream-ring-container ~+version+]])
