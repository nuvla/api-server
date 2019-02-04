(ns com.sixsq.slipstream.ssclj.resources.spec.description-test
  (:require
    [clojure.test :refer [are deftest is]]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as desc]
    [com.sixsq.slipstream.ssclj.resources.spec.description :as desc-spec]
    [com.sixsq.slipstream.ssclj.resources.spec.spec-test-utils :as stu]))


(deftest check-parameter-type

  (doseq [v #{"string" "boolean" "int" "float" "timestamp" "enum" "map" "list"}]
    (stu/is-valid ::desc-spec/type v))

  (stu/is-invalid ::desc-spec/type "unknown"))


(deftest check-parameter-desd-and-resource-desc
  (let [valid-acl {:owner {:principal "me" :type "USER"}}
        valid-desc {:displayName "ID"
                    :category    "common"
                    :description "unique resource identifier"
                    :type        "enum"
                    :mandatory   true
                    :readOnly    true
                    :order       0
                    :enum        ["andorra" "burmuda" "canada"]
                    :autocomplete "country"}
        resource-desc {:identifier valid-desc
                       :other      valid-desc
                       :acl        valid-acl}]

    (stu/is-valid ::desc-spec/parameter-description valid-desc)

    (doseq [k #{:category :description :mandatory :readOnly :order :enum :autocomplete}]
      (stu/is-valid ::desc-spec/parameter-description (dissoc valid-desc k)))

    (stu/is-invalid ::desc-spec/parameter-description (assoc valid-desc :displayName 1))
    (stu/is-invalid ::desc-spec/parameter-description (assoc valid-desc :category 1))
    (stu/is-invalid ::desc-spec/parameter-description (assoc valid-desc :description 1))
    (stu/is-invalid ::desc-spec/parameter-description (assoc valid-desc :type "unknown"))
    (stu/is-invalid ::desc-spec/parameter-description (assoc valid-desc :mandatory 1))
    (stu/is-invalid ::desc-spec/parameter-description (assoc valid-desc :readOnly 1))
    (stu/is-invalid ::desc-spec/parameter-description (assoc valid-desc :readOnly "1"))
    (stu/is-invalid ::desc-spec/parameter-description (assoc valid-desc :enum "1"))
    (stu/is-invalid ::desc-spec/parameter-description (assoc valid-desc :enum ["a" 1]))

    (stu/is-valid ::desc-spec/resource-description resource-desc)
    (stu/is-invalid ::desc-spec/resource-description (assoc resource-desc :another 1))
    (stu/is-valid ::desc-spec/resource-description (assoc desc/CommonParameterDescription :acl valid-acl))))
