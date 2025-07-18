(ns com.sixsq.nuvla.server.resources.spec.credential-totp-2fa-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.credential :as cred]
    [com.sixsq.nuvla.server.resources.credential-template-totp-2fa :as tmpl-totp]
    [com.sixsq.nuvla.server.resources.spec.credential-totp-2fa :as totp-spec]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl
  {:owners ["group/nuvla-admin"]})


(deftest check-credential-service-docker
  (let [timestamp "1964-08-25T10:00:00.00Z"
        tpl       {:id                    (str cred/resource-type "/uuid")
                   :resource-type         cred/resource-type
                   :created               timestamp
                   :updated               timestamp
                   :acl                   valid-acl

                   :subtype               tmpl-totp/credential-subtype
                   :method                tmpl-totp/method

                   :secret                "some-secret"
                   :initialization-vector "something"}]

    (stu/is-valid ::totp-spec/schema tpl)

    ;; mandatory keywords
    (doseq [k (-> tpl (dissoc :initialization-vector) keys set)]
      (stu/is-invalid ::totp-spec/schema (dissoc tpl k)))

    (doseq [k #{:initialization-vector}]
      (stu/is-valid ::totp-spec/schema (dissoc tpl k)))))
