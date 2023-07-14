(ns sixsq.nuvla.server.resources.spec.credential-swarm-token-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-swarm-token :as swarm-token]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl
  {:owners   ["group/nuvla-admin"]
   :view-acl ["user/jane"]})


(deftest check-credential-service-docker
  (let [timestamp "1964-08-25T10:00:00Z"
        tpl       {:id            (str cred/resource-type "/uuid")
                   :resource-type cred/resource-type
                   :created       timestamp
                   :updated       timestamp
                   :acl           valid-acl

                   :subtype       "swarm-token"
                   :method        "swarm-token"
                   :scope         "MANAGER"

                   :token         "some-swarm-token"}]

    (stu/is-valid ::swarm-token/schema tpl)

    ;; mandatory keywords
    (doseq [k (-> tpl keys set)]
      (stu/is-invalid ::swarm-token/schema (dissoc tpl k)))))
