(ns sixsq.nuvla.server.resources.spec.credential-template-2fa-totp-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-totp-2fa :as tmpl-totp]
    [sixsq.nuvla.server.resources.spec.credential-template-totp-2fa :as
     tmpl-totp-spec]
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

                   :subtype       tmpl-totp/credential-subtype
                   :method        tmpl-totp/method

                   :secret         "some-secret"}]

    (stu/is-valid ::tmpl-totp-spec/schema tpl)

    ;; mandatory keys
    (doseq [k (-> tpl (dissoc :scope :token) keys set)]
      (stu/is-invalid ::tmpl-totp-spec/schema (dissoc tpl k)))

    ;; optional keys
    (doseq [k #{:scope :token}]
      (stu/is-valid ::tmpl-totp-spec/schema (dissoc tpl k)))))
