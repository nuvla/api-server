(ns sixsq.nuvla.server.resources.spec.ts-nuvlaedge-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.resources.spec.ts-nuvlaedge :as ts-nuvlaedge]))


(deftest check-schema
  (let [timestamp "1964-08-25T10:00:00.00Z"]
    (doseq [valid-entry [{:nuvlaedge-id "nuvlabox/1"
                          :cpu          {:capacity            8
                                         :load                4.5
                                         :load-1              4.3
                                         :load-5              5.5
                                         :system-calls        0
                                         :interrupts          13621648
                                         :software-interrupts 37244
                                         :context-switches    382731}
                          :timestamp    timestamp}
                         {:nuvlaedge-id "nuvlabox/1"
                          :ram          {:capacity 4096
                                         :used     1000}
                          :timestamp    timestamp}
                         {:nuvlaedge-id "nuvlabox/1"
                          :disk         {:device   "root"
                                         :capacity 20000
                                         :used     10000}
                          :timestamp    timestamp}
                         {:nuvlaedge-id "nuvlabox/1"
                          :network      {:interface         "eth0"
                                         :bytes-received    5247943
                                         :bytes-transmitted 41213}
                          :timestamp    timestamp}
                         {:nuvlaedge-id      "nuvlabox/1"
                          :power-consumption {:metric-name        "IN_current"
                                              :energy-consumption 2.4
                                              :unit               "A"}
                          :timestamp         timestamp}]]

      (stu/is-valid ::ts-nuvlaedge/schema valid-entry)
      (stu/is-invalid ::ts-nuvlaedge/schema (assoc valid-entry :unknown "value"))

      (doseq [attr #{:nuvlaedge-id :timestamp}]
        (stu/is-invalid ::ts-nuvlaedge/schema (dissoc valid-entry attr)))

      (doseq [attr #{:cpu}]
        (stu/is-valid ::ts-nuvlaedge/schema (dissoc valid-entry attr))))))
