(ns com.sixsq.nuvla.server.resources.spec.user-template-mitreid-registration-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [com.sixsq.nuvla.server.resources.spec.user-template-mitreid :as ut-mitreid]
    [com.sixsq.nuvla.server.resources.user-template :as st]))


(def valid-acl {:owners ["group/nuvla-admin"]})


(deftest check-user-template-mitreid-registration-schema
  (let [timestamp  "1964-08-25T10:00:00Z"
        tpl        {:id            (str st/resource-type "/mitreid")
                    :resource-type st/resource-type
                    :name          "my-template"
                    :description   "my template"
                    :group         "my group"
                    :tags          ["a-1" "b-2"]
                    :created       timestamp
                    :updated       timestamp
                    :acl           valid-acl

                    :method        "mitreid-registration"
                    :instance      "mitreid-registration"}

        create-tpl {:name          "my-create"
                    :description   "my create description"
                    :tags          ["c-3" "d-4"]
                    :resource-type (str st/resource-type "-create")
                    :template      (dissoc tpl :id)}]

    ;; check the registration schema (without href)
    (stu/is-valid ::ut-mitreid/schema tpl)

    (doseq [attr #{:id :resource-type :created :updated :acl :method}]
      (stu/is-invalid ::ut-mitreid/schema (dissoc tpl attr)))

    (doseq [attr #{:name :description :group :tags}]
      (stu/is-valid ::ut-mitreid/schema (dissoc tpl attr)))

    ;; check the create template schema (with href)
    (stu/is-valid ::ut-mitreid/schema-create create-tpl)
    (stu/is-valid ::ut-mitreid/schema-create (assoc-in create-tpl [:template :href] "user-template/abc"))
    (stu/is-invalid ::ut-mitreid/schema-create (assoc-in create-tpl [:template :href] "bad-reference/abc"))

    (doseq [attr #{:resource-type :template}]
      (stu/is-invalid ::ut-mitreid/schema-create (dissoc create-tpl attr)))

    (doseq [attr #{:name :description :group :tags}]
      (stu/is-valid ::ut-mitreid/schema-create (dissoc create-tpl attr)))))
