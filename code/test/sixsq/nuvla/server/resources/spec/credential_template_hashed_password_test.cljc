(ns sixsq.nuvla.server.resources.spec.credential-template-hashed-password-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer :all]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-hashed-password :as hashed-password]
    [sixsq.nuvla.server.resources.spec.credential-template-hashed-password :as hashed-password-spec]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl hashed-password/resource-acl)


(deftest test-credential-template-service-schema-check
  (let [timestamp "1972-10-08T10:00:00.0Z"
        tpl {:id            (str ct/resource-type "/uuid")
             :resource-type p/resource-type
             :created       timestamp
             :updated       timestamp
             :acl           valid-acl

             :type          hashed-password/credential-type
             :method        hashed-password/method

             :password      "hello"}]

    (stu/is-valid ::hashed-password-spec/schema tpl)

    ;; mandatory keys
    (doseq [k (-> tpl (dissoc :password) keys set)]
      (stu/is-invalid ::hashed-password-spec/schema (dissoc tpl k)))

    ;; optional keys
    (doseq [k #{:password}]
      (stu/is-valid ::hashed-password-spec/schema (dissoc tpl k)))))
