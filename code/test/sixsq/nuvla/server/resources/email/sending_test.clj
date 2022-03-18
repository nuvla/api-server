(ns sixsq.nuvla.server.resources.email.sending-test
  (:require
    [clojure.java.io :refer [reader writer]]
    [clojure.test :refer [deftest is testing]]
    [postal.core :as postal]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.email.sending :as t]
    [sixsq.nuvla.server.resources.email.content :as content])
  (:import (clojure.lang ExceptionInfo)))

(deftest send-email
  (with-redefs [postal/send-message (fn [smtp data]
                                      (testing "should call postal send-message"
                                        (is (some? (:subject data)) "should have data with subject")
                                        (is (some? (seq smtp)) "should have smtp"))
                                      {:error :SUCCESS})
                crud/retrieve-by-id-as-admin (fn [_] {:smtp-host "host"})]
    (t/send-email "test@example.com" (content/trial-ending {})))

  (testing "should respond with error if unable to send"
    (with-redefs [postal/send-message (fn [smtp data]
                                        (testing "should call postal send-message"
                                          (is (some? (:subject data)) "should have data with subject")
                                          (is (some? (seq smtp)) "should have smtp"))
                                        {:error :ERROR})
                  crud/retrieve-by-id-as-admin (fn [_] {:smtp-host "host"})]
      (is (thrown-with-msg? ExceptionInfo (re-pattern "server configuration for SMTP is missing")
                            (t/send-email "test@example.com" (content/trial-ended {}))))
      (try 
        (t/send-email "test@example.com" (content/trial-ended {}))
        (catch Exception ex
          (let [{:keys [status]} (ex-data ex)]
            (is (= 500 status))))))))
