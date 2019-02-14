(def +version+ "0.0.1-SNAPSHOT")

(defproject sixsq.nuvla.server/cimi-test-jar "0.0.1-SNAPSHOT"

  :description "cimi server testing utilities"

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

  :dependencies [[sixsq.nuvla.server/db-binding-jar ~+version+]
                 [sixsq.nuvla.server/db-testing-jar ~+version+ :scope "compile"]
                 [org.apache.curator/curator-test :scope "compile"]
                 [peridot :scope "compile"]
                 [expound :scope "compile"]
                 [org.clojure/data.json]
                 [compojure]
                 [com.cemerick/url]
                 [sixsq.nuvla.ring/code ~+version+]])
