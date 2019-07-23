(ns sixsq.nuvla.server.resources.spec.user-template-minimum-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.resources.spec.user-template-minimum :as spec-minimum]
    [sixsq.nuvla.server.resources.user-template :as user-tpl]
    [sixsq.nuvla.server.resources.user-template-minimum :as minimum]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(deftest check-user-template-minimum-schema
  (let [timestamp  "1964-08-25T10:00:00.00Z"
        tpl        {:id            (str user-tpl/resource-type "/" minimum/registration-method)
                    :resource-type user-tpl/resource-type
                    :name          "my-template"
                    :description   "my template"
                    :group         "my group"
                    :tags          #{"1", "2"}
                    :created       timestamp
                    :updated       timestamp
                    :acl           valid-acl

                    :method        minimum/registration-method
                    :instance      minimum/registration-method

                    :username      "super"
                    :email         "jane@example.com"}

        create-tpl {:name          "my-create"
                    :description   "my create description"
                    :tags          #{"3", "4"}
                    :resource-type user-tpl/collection-type
                    :template      (dissoc tpl :id)}]

    ;; check the registration schema (without href)
    (stu/is-valid ::spec-minimum/schema tpl)

    ;; mandatory attributes
    (doseq [attr #{:id :resource-type :created :updated :acl :method}]
      (stu/is-invalid ::spec-minimum/schema (dissoc tpl attr)))

    (doseq [attr #{:name :description :tags :username :email}]
      (stu/is-valid ::spec-minimum/schema (dissoc tpl attr)))

    ;; check the create template schema (with href)
    (stu/is-valid ::spec-minimum/schema-create create-tpl)
    (stu/is-valid ::spec-minimum/schema-create (assoc-in create-tpl [:template :href] "user-template/abc"))
    (stu/is-invalid ::spec-minimum/schema-create (assoc-in create-tpl [:template :href] "bad-reference/abc"))

    ;; mandatory attributes
    (doseq [attr #{:resource-type :template}]
      (stu/is-invalid ::spec-minimum/schema-create (dissoc create-tpl attr)))

    ;; optional template attributes
    (doseq [attr #{:username :email}]
      (let [create-tpl (assoc create-tpl :template (dissoc tpl :id attr))]
        (stu/is-valid ::spec-minimum/schema-create create-tpl)))

    ;; optional attributes
    (doseq [attr #{:name :description :tags}]
      (stu/is-valid ::spec-minimum/schema-create (dissoc create-tpl attr)))))
