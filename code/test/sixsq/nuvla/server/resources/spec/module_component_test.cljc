(ns sixsq.nuvla.server.resources.spec.module-component-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.module-component :as t]
    [sixsq.nuvla.server.resources.spec.module-component :as module-component]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})


(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00Z"
        root      {:id                      (str t/resource-type "/module-component-uuid")
                   :resource-type           t/resource-type
                   :created                 timestamp
                   :updated                 timestamp
                   :acl                     valid-acl

                   :author                  "someone"
                   :commit                  "wip"

                   :architectures           ["amd64" "arm/v6"]

                   :image                   {:repository "my-repo"
                                             :image-name "ubuntu"
                                             :tag        "16.04"}

                   :cpus                    1.3
                   :memory                  256

                   :restart-policy          {:condition    "any"
                                             :delay        10
                                             :max-attempts 5
                                             :window       600}

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

                   :private-registries      ["infrastructure-service/uuid-1"
                                             "infrastructure-service/uuid-2"]

                   :output-parameters       [{:name        "alpha"
                                              :description "my-alpha"}
                                             {:name        "beta"
                                              :description "my-beta"}
                                             {:name        "gamma"
                                              :description "my-gamma"}]}]

    (stu/is-valid ::module-component/schema root)
    (stu/is-invalid ::module-component/schema (assoc root :badKey "badValue"))
    (stu/is-invalid ::module-component/schema (assoc root :os "BAD_OS"))

    (stu/is-invalid ::module-component/schema
                    (assoc root :environmental-variables [{:name "NUVLA_RESERVED"}]))
    (stu/is-invalid ::module-component/schema
                    (assoc root :environmental-variables [{:name "1_something"}]))
    (stu/is-valid ::module-component/schema
                  (assoc root :environmental-variables [{:name "something_1"}]))

    ;; required attributes
    (doseq [k #{:id :resource-type :created :updated :acl :author :architectures :image}]
      (stu/is-invalid ::module-component/schema (dissoc root k)))

    ;; optional attributes
    (doseq [k #{:commit :ports :urls :environmental-variables :output-parameters
                :private-registries}]
      (stu/is-valid ::module-component/schema (dissoc root k)))))
