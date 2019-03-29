(ns sixsq.nuvla.server.resources.spec.user-template-username-password-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.resources.spec.user-template-username-password :as spec-username-password]
    [sixsq.nuvla.server.resources.user-template :as user-tpl]
    [sixsq.nuvla.server.resources.user-template-username-password :as username-password]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(deftest check-user-template-password-schema
  (let [timestamp "1964-08-25T10:00:00.0Z"
        tpl {:id                (str user-tpl/resource-type "/" username-password/registration-method)
             :resource-type     user-tpl/resource-type
             :name              "my-template"
             :description       "my template"
             :group             "my group"
             :tags              #{"1", "2"}
             :created           timestamp
             :updated           timestamp
             :acl               valid-acl

             :method            username-password/registration-method
             :instance          username-password/registration-method

             :password          "plaintext-password"
             :password-repeated "plaintext-password"
             :username          "super"}

        create-tpl {:name          "my-create"
                    :description   "my create description"
                    :tags          #{"3", "4"}
                    :resource-type user-tpl/collection-type
                    :template      (dissoc tpl :id)}]

    ;; check the registration schema (without href)
    (stu/is-valid ::spec-username-password/schema tpl)

    ;; mandatory attributes
    (doseq [attr #{:id :resource-type :created :updated :acl :method}]
      (stu/is-invalid ::spec-username-password/schema (dissoc tpl attr)))

    (doseq [attr #{:name :description :tags :password :password-repeated :email}]
      (stu/is-valid ::spec-username-password/schema (dissoc tpl attr)))

    ;; check the create template schema (with href)
    (stu/is-valid ::spec-username-password/schema-create create-tpl)
    (stu/is-valid ::spec-username-password/schema-create (assoc-in create-tpl [:template :href] "user-template/abc"))
    (stu/is-invalid ::spec-username-password/schema-create (assoc-in create-tpl [:template :href] "bad-reference/abc"))

    ;; mandatory attributes
    (doseq [attr #{:resource-type :template}]
      (stu/is-invalid ::spec-username-password/schema-create (dissoc create-tpl attr)))

    ;; mandatory template attributes
    (doseq [attr #{:password :password-repeated :email}]
      (let [create-tpl (assoc create-tpl :template (dissoc tpl attr))]
        (stu/is-invalid ::spec-username-password/schema-create create-tpl)))

    ;; optional attributes
    (doseq [attr #{:name :description :tags}]
      (stu/is-valid ::spec-username-password/schema-create (dissoc create-tpl attr)))))
