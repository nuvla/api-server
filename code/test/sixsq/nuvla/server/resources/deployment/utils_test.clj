(ns sixsq.nuvla.server.resources.deployment.utils-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.deployment.utils :as t]))


(deftest merge-module-test
  (let [dep-app    {:module
                    {:content
                     {:output-parameters       [{:name        "alpha"
                                                 :description "my-alpha"}
                                                {:name        "beta"
                                                 :description "my-beta"
                                                 :value       "beta-inherited-value"}
                                                {:name        "gamma"
                                                 :description "my-gamma"}]

                      :environmental-variables [{:name "ALPHA_ENV"}
                                                {:name        "BETA_ENV"
                                                 :description "beta-env variable"
                                                 :required    true}]

                      :files                   [{:file-name    "my-config.conf"
                                                 :file-content "file content example"}
                                                {:file-name    "file_1"
                                                 :file-content "inherited file content example"}]}}}
        new-module {:content
                    {:output-parameters [{:name        "beta"
                                          :description "my-beta"
                                          :value       "beta-value"}
                                         {:name        "gamma"
                                          :description "my-gamma"
                                          :value       "gamma-value"}]

                     :files             [{:file-name    "my-config.conf"
                                          :file-content "file content example"}
                                         {:file-name    "file_1"
                                          :file-content "file content example"}
                                         {:file-name    "file_1"
                                          :file-content "new file content example"}]}}]

    (is (= {:content {:files             [{:file-content "file content example"
                                           :file-name    "my-config.conf"}
                                          {:file-content "inherited file content example"
                                           :file-name    "file_1"}]
                      :output-parameters [{:description "my-beta"
                                           :name        "beta"
                                           :value       "beta-inherited-value"}
                                          {:description "my-gamma"
                                           :name        "gamma"
                                           :value       "gamma-value"}]}}
           (t/merge-module (:module dep-app) new-module)))))
