(ns sixsq.nuvla.server.resources.spec.credential-infrastructure-service-helm-repo-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service-helm-repo
     :as cred-infra-service-helm-repo]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl
  {:owners   ["group/nuvla-admin"]
   :view-acl ["user/jane"]})


(deftest check-credential-service-registry
  (let [timestamp "1972-10-08T10:00:00.00Z"
        tpl       {:id            (str cred/resource-type "/uuid")
                   :resource-type cred/resource-type
                   :created       timestamp
                   :updated       timestamp
                   :acl           valid-acl

                   :subtype       "registry"
                   :method        "registry"

                   :parent        "infrastructure-service/service-1"

                   :username      "username"
                   :password      "password"}]

    (stu/is-valid ::cred-infra-service-helm-repo/schema tpl)

    ;; mandatory keywords
    (doseq [k (-> tpl keys set)]
      (stu/is-invalid ::cred-infra-service-helm-repo/schema (dissoc tpl k)))

    ;; optional keywords
    (doseq [k #{}]
      (stu/is-valid ::cred-infra-service-helm-repo/schema (dissoc tpl k)))))
