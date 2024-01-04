(ns sixsq.nuvla.server.resources.spec.ts-nuvlaedge-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.resources.spec.ts-nuvlaedge :as ts-nuvlaedge]))

(deftest check-schema
  (let [timestamp   "1964-08-25T10:00:00.00Z"
        valid-entry {:nuvlaedge-id "nuvlabox/1"
                     :load         1.2
                     :mem          5000
                     :timestamp    timestamp}]

    (stu/is-valid ::ts-nuvlaedge/schema valid-entry)
    (stu/is-invalid ::ts-nuvlaedge/schema (assoc valid-entry :unknown "value"))

    (doseq [attr #{:nuvlaedge-id :timestamp}]
      (stu/is-invalid ::ts-nuvlaedge/schema (dissoc valid-entry attr)))

    (doseq [attr #{:load :mem}]
      (stu/is-valid ::ts-nuvlaedge/schema (dissoc valid-entry attr)))))
