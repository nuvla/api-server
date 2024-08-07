(ns com.sixsq.nuvla.server.resources.spec.data-object-template-generic-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.data-object-template :as dot]
    [com.sixsq.nuvla.server.resources.data-object-template-generic :as tpl]
    [com.sixsq.nuvla.server.resources.spec.data-object-template-generic :as dot-generic]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(deftest test-schema-check
  (let [root (merge tpl/resource
                    {:href       "data-object-template/generic"
                     :credential "credential/d3167d53-0138-4754-b8fd-df8119474e7f"
                     :bucket     "bucket"
                     :object     "object/name"})]

    (stu/is-valid ::dot-generic/template root)

    ;; mandatory keywords
    (doseq [k #{:subtype :credential :bucket :object}]
      (stu/is-invalid ::dot-generic/template (dissoc root k)))

    ;; optional keywords
    (doseq [k #{:content-type :href}]
      (stu/is-valid ::dot-generic/template (dissoc root k)))


    (let [create {:resource-type (str dot/resource-type "-create")
                  :template      (dissoc root :id)}]
      (stu/is-valid ::dot-generic/schema-create create))))
