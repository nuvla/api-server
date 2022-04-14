(ns sixsq.nuvla.server.resources.spec.user-template-email-password-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.resources.spec.user-template-email-password :as spec-email-password]
    [sixsq.nuvla.server.resources.user-template :as user-tpl]
    [sixsq.nuvla.server.resources.user-template-email-password :as email-password]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(deftest check-user-template-password-schema
  (let [timestamp  "1964-08-25T10:00:00.00Z"
        tpl        {:id            (str user-tpl/resource-type "/" email-password/registration-method)
                    :resource-type user-tpl/resource-type
                    :name          "my-template"
                    :description   "my template"
                    :group         "my group"
                    :tags          #{"1", "2"}
                    :created       timestamp
                    :updated       timestamp
                    :acl           valid-acl

                    :method        email-password/registration-method
                    :instance      email-password/registration-method

                    :password      "plaintext-password"
                    :email         "someone@example.org"}

        create-tpl {:name          "my-create"
                    :description   "my create description"
                    :tags          #{"3", "4"}
                    :resource-type user-tpl/collection-type
                    :template      (dissoc tpl :id)}]

    ;; check the registration schema (without href)
    (stu/is-valid ::spec-email-password/schema tpl)

    ;; mandatory attributes
    (doseq [attr #{:id :resource-type :created :updated :acl :method}]
      (stu/is-invalid ::spec-email-password/schema (dissoc tpl attr)))

    (doseq [attr #{:name :description :tags :password :email}]
      (stu/is-valid ::spec-email-password/schema (dissoc tpl attr)))

    ;; check the create template schema (with href)
    (stu/is-valid ::spec-email-password/schema-create create-tpl)
    (stu/is-valid ::spec-email-password/schema-create (assoc-in create-tpl [:template :href] "user-template/abc"))
    (stu/is-invalid ::spec-email-password/schema-create (assoc-in create-tpl [:template :href] "bad-reference/abc"))

    ;; check the create template schema with a customer
    (stu/is-valid ::spec-email-password/schema-create
                  (assoc-in create-tpl [:template :customer]
                            {:fullname       "toto"
                             :address        {:street-address "Av. quelque chose"
                                              :city           "Meyrin"
                                              :country        "CH"
                                              :postal-code    "1217"}
                             :subscription?  true
                             :payment-method "pm_something"}))


    ;; mandatory attributes
    (doseq [attr #{:resource-type :template}]
      (stu/is-invalid ::spec-email-password/schema-create (dissoc create-tpl attr)))

    ;; mandatory template attributes
    (doseq [attr #{:password :email}]
      (let [create-tpl (assoc create-tpl :template (dissoc tpl attr))]
        (stu/is-invalid ::spec-email-password/schema-create create-tpl)))

    ;; optional attributes
    (doseq [attr #{:name :description :tags}]
      (stu/is-valid ::spec-email-password/schema-create (dissoc create-tpl attr)))))
