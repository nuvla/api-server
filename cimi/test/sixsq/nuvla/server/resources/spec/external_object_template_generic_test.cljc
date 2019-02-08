(ns sixsq.nuvla.server.resources.spec.external-object-template-generic-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.external-object-template :as eot]
    [sixsq.nuvla.server.resources.external-object-template-generic :as tpl]
    [sixsq.nuvla.server.resources.spec.external-object-template-generic :as eot-generic]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(deftest test-schema-check
  (let [root (merge tpl/resource
                    {:href "external-object-template/generic"})]

    (stu/is-valid ::eot-generic/template root)

    ;; mandatory keywords
    (doseq [k #{:objectType :objectStoreCred :bucketName :objectName}]
      (stu/is-invalid ::eot-generic/template (dissoc root k)))

    ;; optional keywords
    (doseq [k #{:contentType :href}]
      (stu/is-valid ::eot-generic/template (dissoc root k)))


    (let [create {:resource-type (str eot/resource-type "-create")
                  :template      (dissoc root :id)}]
      (stu/is-valid ::eot-generic/external-object-create create))))
