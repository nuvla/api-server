(ns com.sixsq.slipstream.ssclj.resources.spec.external-object-template-report-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.slipstream.ssclj.resources.external-object-template :as eot]
    [com.sixsq.slipstream.ssclj.resources.external-object-template-report :as tpl]
    [com.sixsq.slipstream.ssclj.resources.spec.external-object-template-report :as eot-report]
    [com.sixsq.slipstream.ssclj.resources.spec.spec-test-utils :as stu]))


(deftest test-schema-check
  (let [root (merge tpl/resource
                    {:href     "external-object-template/report"
                     :filename "component.1_report_time.tgz"})]

    (stu/is-valid ::eot-report/externalObjectTemplate root)

    ;; mandatory keywords
    (doseq [k #{:objectType :runUUID :component :filename}]
      (stu/is-invalid ::eot-report/externalObjectTemplate (dissoc root k)))

    ;; optional keywords
    (doseq [k #{:contentType :href}]
      (stu/is-valid ::eot-report/externalObjectTemplate (dissoc root k)))


    (let [create {:resourceURI            (str eot/resource-uri "Create")
                  :externalObjectTemplate (dissoc root :id)}]
      (stu/is-valid ::eot-report/external-object-create create))))
