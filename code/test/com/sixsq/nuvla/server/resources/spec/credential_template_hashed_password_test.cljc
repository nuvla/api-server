(ns com.sixsq.nuvla.server.resources.spec.credential-template-hashed-password-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.credential :as p]
    [com.sixsq.nuvla.server.resources.credential-template :as ct]
    [com.sixsq.nuvla.server.resources.credential-template-hashed-password :as hashed-password]
    [com.sixsq.nuvla.server.resources.spec.credential-template-hashed-password :as hashed-password-spec]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl hashed-password/resource-acl)


(deftest test-credential-template-service-schema-check
  (let [timestamp "1972-10-08T10:00:00.00Z"
        tpl       {:id            (str ct/resource-type "/uuid")
                   :resource-type p/resource-type
                   :created       timestamp
                   :updated       timestamp
                   :acl           valid-acl

                   :subtype       hashed-password/credential-subtype
                   :method        hashed-password/method

                   :password      "hello"}]

    (stu/is-valid ::hashed-password-spec/schema tpl)

    ;; mandatory keys
    (doseq [k (-> tpl (dissoc :password) keys set)]
      (stu/is-invalid ::hashed-password-spec/schema (dissoc tpl k)))

    ;; optional keys
    (doseq [k #{:password}]
      (stu/is-valid ::hashed-password-spec/schema (dissoc tpl k)))))
