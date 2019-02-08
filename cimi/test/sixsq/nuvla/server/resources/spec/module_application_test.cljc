(ns sixsq.nuvla.server.resources.spec.module-application-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.connector :as t]  ;; FIXME: Change to module-version when available.
    [sixsq.nuvla.server.resources.spec.module-application :as module-app]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "ANON"
                         :type      "ROLE"
                         :right     "VIEW"}]})


(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.0Z"
        root {:id            (str t/resource-type "/connector-uuid")
              :resource-type t/resource-type
              :created       timestamp
              :updated       timestamp
              :acl           valid-acl

              :nodes         [{:node         "node_alpha"
                               :component    {:href "module/a-b"}
                               :multiplicity 1}
                              {:node                    "node_beta"
                               :component               {:href "module/c-d"}
                               :multiplicity            2
                               :maxProvisioningFailures 1}
                              {:node                    "node_gamma"
                               :component               {:href "module/e-f"}
                               :multiplicity            20
                               :maxProvisioningFailures 10
                               :parameterMappings       [{:parameter "param.1-2" :mapped true, :value "param.2-3"}
                                                         {:parameter "param.3-4" :mapped false, :value "default"}]}]
              :author        "someone"
              :commit        "wip"}]

    (stu/is-valid ::module-app/module-application root)
    (stu/is-invalid ::module-app/module-application (assoc root :badKey "badValue"))

    ;; required attributes
    (doseq [k #{:id :resource-type :created :updated :acl :author}]
      (stu/is-invalid ::module-app/module-application (dissoc root k)))

    ;; optional attributes
    (doseq [k #{:nodes :parameterMappings :commit}]
      (stu/is-valid ::module-app/module-application (dissoc root k)))))
