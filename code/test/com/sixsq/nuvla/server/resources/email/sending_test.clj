(ns com.sixsq.nuvla.server.resources.email.sending-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.email.content :as content]
    [com.sixsq.nuvla.server.resources.email.sending :as t]
    [postal.core :as postal])
  (:import (clojure.lang ExceptionInfo)))

(deftest send-email
  (with-redefs [postal/send-message (fn [smtp data]
                                      (testing "should call postal send-message"
                                        (is (some? (:subject data)) "should have data with subject")
                                        (is (some? (seq smtp)) "should have smtp"))
                                      {:error :SUCCESS})
                crud/retrieve-by-id-as-admin (fn [_] {:smtp-host "host"})]
    (is (= {:success? true} (t/send-email "test@example.com" (content/trial-ending {})))
        "should return success"))

  (testing "should throw error if unable to send"
    (with-redefs [postal/send-message (fn [smtp data]
                                        (testing "should call postal send-message"
                                          (is (some? (:subject data)) "should have data with subject")
                                          (is (some? (seq smtp)) "should have smtp"))
                                        {:error :ERROR})
                  crud/retrieve-by-id-as-admin (fn [_] {:smtp-host "host"})]
      (is (thrown-with-msg? ExceptionInfo (re-pattern "email dispatch failed!")
                            (t/send-email "test@example.com" (content/trial-ended {}))))
      (try 
        (t/send-email "test@example.com" (content/trial-ended {}))
        (catch Exception ex
          (let [{:keys [status]} (ex-data ex)]
            (is (= 500 status))))))))
