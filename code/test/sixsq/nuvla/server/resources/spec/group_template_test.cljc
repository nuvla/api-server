(ns sixsq.nuvla.server.resources.spec.group-template-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.group-template :as group-tpl]
    [sixsq.nuvla.server.resources.spec.group-template :as group-tpl-spec]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(deftest check-group-template-password-schema
  (let [timestamp  "1964-08-25T10:00:00.00Z"
        tpl        {:id               (str group-tpl/resource-type "/generic")
                    :resource-type    group-tpl/resource-type
                    :name             "my-template"
                    :description      "my template"
                    :tags             #{"1", "2"}
                    :created          timestamp
                    :updated          timestamp
                    :acl              valid-acl

                    :group-identifier "my-valid-identifier"}

        create-tpl {:name          "my-create"
                    :description   "my create description"
                    :tags          #{"3", "4"}
                    :resource-type group-tpl/collection-type
                    :template      (dissoc tpl :id)}]

    ;; check the template resource schema
    (stu/is-valid ::group-tpl-spec/schema tpl)

    ;; mandatory attributes
    (doseq [attr #{:id :resource-type :created :updated :acl}]
      (stu/is-invalid ::group-tpl-spec/schema (dissoc tpl attr)))

    ;; optional attributes
    (doseq [attr #{:name :description :tags :group-identifier}]
      (stu/is-valid ::group-tpl-spec/schema (dissoc tpl attr)))

    ;; check the create template schema (with href)
    (stu/is-valid ::group-tpl-spec/schema-create create-tpl)
    (stu/is-valid ::group-tpl-spec/schema-create (assoc-in create-tpl [:template :href] "group-template/generic"))
    (stu/is-invalid ::group-tpl-spec/schema-create (assoc-in create-tpl [:template :href] "bad-reference/abc"))

    ;; mandatory attributes
    (doseq [attr #{:resource-type :template}]
      (stu/is-invalid ::group-tpl-spec/schema-create (dissoc create-tpl attr)))

    ;; mandatory template attributes
    (doseq [attr #{:group-identifier}]
      (let [create-tpl (assoc create-tpl :template (dissoc tpl attr))]
        (stu/is-invalid ::group-tpl-spec/schema-create create-tpl)))

    ;; optional attributes
    (doseq [attr #{:name :description :tags}]
      (stu/is-valid ::group-tpl-spec/schema-create (dissoc create-tpl attr)))))
