(ns sixsq.nuvla.server.resources.spec.credential-template-swarm-token-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-swarm-token :as swarm-token]
    [sixsq.nuvla.server.resources.spec.credential-template-swarm-token :as swarm-token-spec]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl
  {:owners   ["group/nuvla-admin"]
   :view-acl ["user/jane"]})


(deftest test-credential-template-service-schema-check
  (let [timestamp "1972-10-08T10:00:00.00Z"
        tpl       {:id            (str ct/resource-type "/uuid")
                   :resource-type p/resource-type
                   :created       timestamp
                   :updated       timestamp
                   :acl           valid-acl

                   :subtype       swarm-token/credential-subtype
                   :method        swarm-token/method

                   :scope         "MANAGER"
                   :token         "some-swarm-token"}]

    (stu/is-valid ::swarm-token-spec/schema tpl)

    ;; mandatory keys
    (doseq [k (-> tpl (dissoc :scope :token) keys set)]
      (stu/is-invalid ::swarm-token-spec/schema (dissoc tpl k)))

    ;; optional keys
    (doseq [k #{:scope :token}]
      (stu/is-valid ::swarm-token-spec/schema (dissoc tpl k)))))
