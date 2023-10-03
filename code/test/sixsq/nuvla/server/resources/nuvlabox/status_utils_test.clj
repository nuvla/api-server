(ns sixsq.nuvla.server.resources.nuvlabox.status-utils-test
  (:require
    [clojure.test :refer [are deftest is testing]]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.nuvlabox.status-utils :as t]))

(deftest status-fields-to-denormalize
  (are [expected nuvlabox-status]
    (= expected (t/status-fields-to-denormalize nuvlabox-status))
    {} nil
    {} {:other true}
    {:online true} {:online true}

    {:online false :inferred-location [46.2 6.1]}
    {:online false :inferred-location [46.2 6.1] :other false}

    {:inferred-location [46.2 6.1] :nuvlabox-engine-version "2.9.0"}
    {:inferred-location [46.2 6.1] :nuvlabox-engine-version "2.9.0" :other "x"}))

(deftest denormalize-changes-nuvlabox
  (testing "nuvlabox is not retrieved when no fields to be propagated"
    (let [called (atom false)]
      (with-redefs [crud/retrieve-by-id-as-admin #(reset! called true)]
        (t/denormalize-changes-nuvlabox {:other "x"})
        (is (false? @called)))))
  (testing "nuvlabox is edited when propagated field value is changed"
    (with-redefs [crud/retrieve-by-id-as-admin (constantly {:online true})
                  db/scripted-edit             (fn [_id {:keys [doc]}] (is (false? (:online doc))))]
      (t/denormalize-changes-nuvlabox {:online false})))
  (testing "nuvlabox is edited when some propagated fields values are changed"
    (with-redefs [crud/retrieve-by-id-as-admin (constantly {:online                  true
                                                            :nuvlabox-engine-version "1.0.0"})
                  db/scripted-edit             (fn [_id {:keys [doc]}] (is (= {:inferred-location       [46.2 6.1]
                                                                               :nuvlabox-engine-version "2.0.0"
                                                                               :online                  true}
                                                                              doc)))]
      (t/denormalize-changes-nuvlabox {:online                  true
                                       :inferred-location       [46.2 6.1]
                                       :nuvlabox-engine-version "2.0.0"}))))
