(defproject com.sixsq.nuvla/api-server-test "version-is-inherited"

            :description "core api server test"

            :url "https://github.com/nuvla/api-server"

            :plugins [[lein-parent "0.3.5"]]

            :parent-project {:path    "../project.clj"
                             :inherit [:version
                                       :license
                                       :url
                                       :plugins
                                       :min-lein-version
                                       :repositories
                                       :deploy-repositories
                                       :pom-location
                                       [:profiles :provided]]}
            :source-paths ["../test"]
            :clean-targets ^{:protect false} ["target"]
            )
