(ns sixsq.nuvla.server.resources.spec.data-object-template-public-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.data-object-template :as dot]
    [sixsq.nuvla.server.resources.data-object-template-public :as tpl]
    [sixsq.nuvla.server.resources.spec.data-object-template-public :as dot-public]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(deftest test-schema-check
  (let [root (merge tpl/resource
                    {:href       "data-object-template/public"
                     :credential "credential/cloud-cred"
                     :bucket     "bucket"
                     :object     "object/name"})]

    (stu/is-valid ::dot-public/template root)

    ;; mandatory keywords
    (doseq [k #{:type :credential :bucket :object}]
      (stu/is-invalid ::dot-public/template (dissoc root k)))

    ;; optional keywords
    (doseq [k #{:content-type :href}]
      (stu/is-valid ::dot-public/template (dissoc root k)))


    (let [create {:resource-type (str dot/resource-type "-create")
                  :template      (dissoc root :id)}]
      (stu/is-valid ::dot-public/schema-create create))))
