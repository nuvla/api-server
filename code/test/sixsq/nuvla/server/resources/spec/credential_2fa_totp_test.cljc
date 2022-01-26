(ns sixsq.nuvla.server.resources.spec.credential-2fa-totp-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-2fa-totp :as totp-spec]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.resources.credential-template-2fa-totp :as tmpl-totp]))


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
