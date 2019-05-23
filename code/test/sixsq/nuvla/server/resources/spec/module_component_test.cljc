(ns sixsq.nuvla.server.resources.spec.module-component-test
  (:require
    [clojure.test :refer [are deftest is]]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.server.resources.module-component :as t]
    [sixsq.nuvla.server.resources.spec.module-component :as module-component]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})


(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.00Z"
        root {:id                      (str t/resource-type "/module-component-uuid")
              :resource-type           t/resource-type
              :created                 timestamp
              :updated                 timestamp
              :acl                     valid-acl

              :author                  "someone"
              :commit                  "wip"

              :architecture            "x86"

              :image                   {:repository "my-repo"
                                        :image-name "ubuntu"
                                        :tag        "16.04"}

              :mounts                  [{:mount-type "bind"
                                         :source     "/abc/file"
                                         :target     "/file"
                                         :read-only  false}
                                        {:mount-type     "volume"
                                         :source         "/nfs-server/nfs-path"
                                         :target         "/mnt"
                                         :volume-options {:o      "addr=1.2.3.4"
                                                          :device "nfs-server/nfs-path"
                                                          :type   "nfs"
                                                          :vers   "4"}}]

              :ports                   [{:protocol       "tcp"
                                         :target-port    22
                                         :published-port 8022}
                                        {:target-port 333}]

              :urls                    [["primary" "https://${host}:${port-443}/my/path"]
                                        ["other" "http://${host}:${port-80}/path"]]

              :environmental-variables [{:name  "ALPHA_ENV"
                                         :value "OK"}
                                        {:name        "BETA_ENV"
                                         :description "beta-env variable"
                                         :required    true}]

              :output-parameters       [{:name        "alpha"
                                         :description "my-alpha"}
                                        {:name        "beta"
                                         :description "my-beta"}
                                        {:name        "gamma"
                                         :description "my-gamma"}]}]

    (stu/is-valid ::module-component/schema root)
    (stu/is-invalid ::module-component/schema (assoc root :badKey "badValue"))
    (stu/is-invalid ::module-component/schema (assoc root :os "BAD_OS"))

    ;; required attributes
    (doseq [k #{:id :resource-type :created :updated :acl :author :architecture :image}]
      (stu/is-invalid ::module-component/schema (dissoc root k)))

    ;; optional attributes
    (doseq [k #{:commit :ports :urls :environmental-variables :output-parameters}]
      (stu/is-valid ::module-component/schema (dissoc root k)))))
