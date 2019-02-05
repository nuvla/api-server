(def +version+ "3.69-SNAPSHOT")

(defproject sixsq.nuvla.server/cimi-test-server-jar "3.69-SNAPSHOT"

  :description "complete test server"

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

  :pom-location "target/"

  :aot [com.sixsq.slipstream.ssclj.app.CIMITestServer]

  :dependencies [[org.apache.curator/curator-test :scope "compile"]
                 [sixsq.nuvla.server/cimi-jar ~+version+]
                 [sixsq.nuvla.server/ring ~+version+]])
