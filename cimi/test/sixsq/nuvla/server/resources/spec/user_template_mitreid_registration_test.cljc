(ns sixsq.nuvla.server.resources.spec.user-template-mitreid-registration-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.resources.spec.user-template-mitreid :as ut-mitreid]
    [sixsq.nuvla.server.resources.user-template :as st]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:type      "ROLE",
                         :principal "ADMIN",
                         :right     "ALL"}]})


(deftest check-user-template-mitreid-registration-schema
  (let [timestamp "1964-08-25T10:00:00.0Z"
        tpl {:id          (str st/resource-url "/mitreid")
             :resourceURI st/resource-uri
             :name        "my-template"
             :description "my template"
             :group       "my group"
             :properties  {:a "1", :b "2"}
             :created     timestamp
             :updated     timestamp
             :acl         valid-acl

             :method      "mitreid-registration"
             :instance    "mitreid-registration"}

        create-tpl {:name         "my-create"
                    :description  "my create description"
                    :properties   {:c "3", :d "4"}
                    :resourceURI  "http://sixsq.com/slipstream/1/UserTemplateCreate"
                    :userTemplate (dissoc tpl :id)}]

    ;; check the registration schema (without href)
    (stu/is-valid ::ut-mitreid/schema tpl)

    (doseq [attr #{:id :resourceURI :created :updated :acl :method}]
      (stu/is-invalid ::ut-mitreid/schema (dissoc tpl attr)))

    (doseq [attr #{:name :description :group :properties}]
      (stu/is-valid ::ut-mitreid/schema (dissoc tpl attr)))

    ;; check the create template schema (with href)
    (stu/is-valid ::ut-mitreid/schema-create create-tpl)
    (stu/is-valid ::ut-mitreid/schema-create (assoc-in create-tpl [:userTemplate :href] "user-template/abc"))
    (stu/is-invalid ::ut-mitreid/schema-create (assoc-in create-tpl [:userTemplate :href] "bad-reference/abc"))

    (doseq [attr #{:resourceURI :userTemplate}]
      (stu/is-invalid ::ut-mitreid/schema-create (dissoc create-tpl attr)))

    (doseq [attr #{:name :description :group :properties}]
      (stu/is-valid ::ut-mitreid/schema-create (dissoc create-tpl attr)))))
