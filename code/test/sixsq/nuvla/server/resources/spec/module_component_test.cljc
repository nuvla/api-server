(ns sixsq.nuvla.server.resources.spec.module-component-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.module-component :as t]
    [sixsq.nuvla.server.resources.spec.module-component :as module-component]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})


(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.0Z"
        root {:id                (str t/resource-type "/module-component-uuid")
              :resource-type     t/resource-type
              :created           timestamp
              :updated           timestamp
              :acl               valid-acl

              :author            "someone"
              :commit            "wip"

              :architecture      "x86"
              :image             {:repository "my-repo"
                                  :image-name "ubuntu"
                                  :tag        "16.04"}
              :mounts            [{:mount-type "bind"
                                   :source     "/abc/file"
                                   :target     "/file"
                                   :read-only  false}
                                  {:mount-type     "volume"
                                   :source         "/nfs-server/nfs-path"
                                   :target         "/mnt"
                                   :volume-options [{:option-key   "o"
                                                     :option-value "addr=1.2.3.4"}
                                                    {:option-key   "device"
                                                     :option-value "nfs-server/nfs-path"}
                                                    {:option-key   "type"
                                                     :option-value "nfs"}
                                                    {:option-key   "vers"
                                                     :option-value "4"}
                                                    {:option-key   "dst"
                                                     :option-value "/mnt"}]}]
              :ports             [{:protocol       "tcp"
                                   :target-port    22
                                   :published-port 8022}
                                  {:target-port 333}]
              :urls              [["primary" "https://${host}:${port-443}/my/path"]
                                  ["other" "http://${host}:${port-80}/path"]]
              :output-parameters [{:name        "alpha"
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
    (doseq [k #{:commit :ports :urls :output-parameters}]
      (stu/is-valid ::module-component/schema (dissoc root k)))))
