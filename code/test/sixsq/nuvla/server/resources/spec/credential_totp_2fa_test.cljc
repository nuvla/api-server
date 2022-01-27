(ns sixsq.nuvla.server.resources.spec.credential-totp-2fa-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.credential :as cred]
    [sixsq.nuvla.server.resources.credential-template-totp-2fa :as tmpl-totp]
    [sixsq.nuvla.server.resources.spec.credential-totp-2fa :as totp-spec]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl
  {:owners   ["group/nuvla-admin"]})


(deftest check-credential-service-docker
  (let [timestamp "1964-08-25T10:00:00.00Z"
        tpl       {:id            (str cred/resource-type "/uuid")
                   :resource-type cred/resource-type
                   :created       timestamp
                   :updated       timestamp
                   :acl           valid-acl

                   :subtype       tmpl-totp/credential-subtype
                   :method        tmpl-totp/method

                   :secret        "some-secret"}]

    (stu/is-valid ::totp-spec/schema tpl)

    ;; mandatory keywords
    (doseq [k (-> tpl keys set)]
      (stu/is-invalid ::totp-spec/schema (dissoc tpl k)))))
