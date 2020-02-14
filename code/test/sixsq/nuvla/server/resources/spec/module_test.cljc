(ns sixsq.nuvla.server.resources.spec.module-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.module :as t]
    [sixsq.nuvla.server.resources.spec.module :as module]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})


(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.00Z"
        root      {:id                        (str t/resource-type "/connector-uuid")
                   :resource-type             t/resource-type
                   :created                   timestamp
                   :updated                   timestamp
                   :acl                       valid-acl
                   :parent-path               "a/b"
                   :path                      "a/b/c"
                   :subtype                   "component"
                   :versions                  [{:href   "module-component/xyz"
                                                :author "someone"
                                                :commit "wip"}
                                               nil
                                               {:href "module-component/abc"}]
                   :logo-url                  "https://example.org/logo"

                   :data-accept-content-types ["application/json" "application/x-something"]
                   :data-access-protocols     ["http+s3" "posix+nfs"]
                   :private-registries        ["infrastructure-service/uuid-1"
                                               "infrastructure-service/uuid-2"]}]

    (stu/is-valid ::module/schema root)
    (stu/is-invalid ::module/schema (assoc root :bad-key "badValue"))
    (stu/is-invalid ::module/schema (assoc root :subtype "BAD_VALUE"))

    ;; required attributes
    (doseq [k #{:id :resource-type :created :updated :acl :path :subtype}]
      (stu/is-invalid ::module/schema (dissoc root k)))

    ;; optional attributes
    (doseq [k #{:logo-url :versions :data-accept-content-types :data-access-protocols
                :private-registries}]
      (stu/is-valid ::module/schema (dissoc root k)))))
