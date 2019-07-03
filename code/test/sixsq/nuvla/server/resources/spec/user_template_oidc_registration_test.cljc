(ns sixsq.nuvla.server.resources.spec.user-template-oidc-registration-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.resources.spec.user-template-oidc :as ut-oidc]
    [sixsq.nuvla.server.resources.user-template :as st]))


(def valid-acl {:owners ["group/nuvla-admin"]})


(deftest check-user-template-oidc-registration-schema
  (let [timestamp  "1964-08-25T10:00:00Z"
        tpl        {:id            (str st/resource-type "/internal")
                    :resource-type st/resource-type
                    :name          "my-template"
                    :description   "my template"
                    :group         "my group"
                    :tags          ["a-1" "b-2"]
                    :created       timestamp
                    :updated       timestamp
                    :acl           valid-acl

                    :method        "oidc-registration"
                    :instance      "oidc-registration"}

        create-tpl {:name          "my-create"
                    :description   "my create description"
                    :tags          ["c-3" "d-4"]
                    :resource-type (str st/resource-type "-create")
                    :template      (dissoc tpl :id)}]

    ;; check the registration schema (without href)
    (stu/is-valid ::ut-oidc/schema tpl)

    (doseq [attr #{:id :resource-type :created :updated :acl :method}]
      (stu/is-invalid ::ut-oidc/schema (dissoc tpl attr)))

    (doseq [attr #{:name :description :tags}]
      (stu/is-valid ::ut-oidc/schema (dissoc tpl attr)))

    ;; check the create template schema (with href)
    (stu/is-valid ::ut-oidc/schema-create create-tpl)
    (stu/is-valid ::ut-oidc/schema-create (assoc-in create-tpl [:template :href] "user-template/abc"))
    (stu/is-invalid ::ut-oidc/schema-create (assoc-in create-tpl [:template :href] "bad-reference/abc"))

    (doseq [attr #{:resource-type :template}]
      (stu/is-invalid ::ut-oidc/schema-create (dissoc create-tpl attr)))

    (doseq [attr #{:name :description :tags}]
      (stu/is-valid ::ut-oidc/schema-create (dissoc create-tpl attr)))))
