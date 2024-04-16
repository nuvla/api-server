(ns sixsq.nuvla.server.resources.spec.timeseries-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.timeseries :as t]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.resources.spec.timeseries :as timeseries]
    [sixsq.nuvla.server.util.time :as time]))

(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})

(deftest check-schema
  (let [timestamp   (time/now-str)
        valid-entry {:id            (str t/resource-type "/internal")
                     :resource-type t/resource-type
                     :created       timestamp
                     :updated       timestamp
                     :acl           valid-acl
                     :dimensions    [{:field-name "test-dimension"
                                      :field-type "keyword"}]
                     :metrics       [{:field-name  "test-metric"
                                      :field-type  "long"
                                      :metric-type "gauge"}
                                     {:field-name  "test-optional-metric"
                                      :field-type  "long"
                                      :metric-type "counter"
                                      :optional    true}]}]

    (stu/is-valid ::timeseries/schema valid-entry)
    (stu/is-invalid ::timeseries/schema (assoc valid-entry :unknown "value"))

    (doseq [attr #{:metrics :dimensions}]
      (stu/is-invalid ::timeseries/schema (dissoc valid-entry attr)))))
