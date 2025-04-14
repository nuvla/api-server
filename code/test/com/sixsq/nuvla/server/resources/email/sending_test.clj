(ns com.sixsq.nuvla.server.resources.email.sending-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.email.content :as content]
    [com.sixsq.nuvla.server.resources.email.sending :as t]
    [postal.core :as postal])
  (:import (clojure.lang ExceptionInfo)))

(deftest send-email
  (with-redefs [postal/send-message          (fn [smtp data]
                                               (testing "should call postal send-message"
                                                 (is (some? (:subject data)) "should have data with subject")
                                                 (is (some? (seq smtp)) "should have smtp"))
                                               {:error :SUCCESS})
                crud/retrieve-by-id-as-admin (fn [_] {:smtp-host "host"})]
    (is (= {:success? true} (t/send-email "test@example.com" (content/trial-ending {})))
        "should return success"))

  (testing "should throw error if unable to send"
    (with-redefs [postal/send-message          (fn [smtp data]
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

(deftest extract-smtp-cfg
  (testing "SMTP basic config"
    (is (= (t/extract-smtp-cfg {:smtp-host     "some.smtp.com"
                                :smtp-port     25
                                :smtp-ssl      false
                                :smtp-username "user@smtp.com"
                                :smtp-password "secure-pass"}) {:host "some.smtp.com"
                                                                :pass "secure-pass"
                                                                :port 25
                                                                :ssl  false
                                                                :user "user@smtp.com"})))
  (testing "smtp-xoauth2 set to a value that doesn't exist"
    (is (= (t/extract-smtp-cfg {:smtp-host     "some.smtp.com"
                                :smtp-port     25
                                :smtp-ssl      false
                                :smtp-username "user@smtp.com"
                                :smtp-xoauth2  "not-exiting"}) nil)))

  (testing "smtp-xoauth2 set to a value that exist"
    (with-redefs [t/get-google-access-token (constantly "foo-bar")]
      (is (= (t/extract-smtp-cfg {:smtp-host     "some.smtp.com"
                                  :smtp-port     25
                                  :smtp-ssl      false
                                  :smtp-username "user@smtp.com"
                                  :smtp-xoauth2  "google"})
             {:auth.mechanisms "XOAUTH2"
              :host            "some.smtp.com"
              :pass            "foo-bar"
              :port            25
              :ssl             false
              :user            "user@smtp.com"})))))

(deftest get-google-access-token
  (let [config {:client-id     "cid"
                :client-secret "cis"
                :refresh-token "rt"}]
    (testing "missing refresh-token param"
      (is (= (t/get-google-access-token (dissoc config :refresh-token)) nil)))

    (testing "should work"
      (with-redefs [t/refresh-token-when-no-access-token-or-on-config-change!
                    (fn [c]
                      (is (= config c))
                      (reset! t/access-token-response! {"access_token" "foo-bar"}))]
        (is (= (t/get-google-access-token config) "foo-bar"))))))

(deftest compute-next-refresh-ms
  (is (= (t/compute-next-refresh-ms {}) 5000))
  (is (= (t/compute-next-refresh-ms {"expires_in" 1}) 5000))
  (is (= (t/compute-next-refresh-ms {"expires_in" 30}) 5000))
  (is (= (t/compute-next-refresh-ms {"expires_in" 36}) 6000))
  (is (= (t/compute-next-refresh-ms {"expires_in" 3600}) 3570000)))

(deftest schedule-refresh-token-before-expiry
  (let [sleep-args            (atom [])
        elements-to-check     3
        wait-for-two-elements #(let [f (future
                                         (while (< (count @sleep-args) elements-to-check)
                                           (Thread/sleep 10)))]
                                 (deref f 2000 nil))]
    (with-redefs [t/access-token-response!           (atom nil)
                  t/xoauth2-future!                  (atom nil)
                  t/sleep                            #(swap! sleep-args conj %)
                  t/post-google-refresh-access-token #(reset! t/access-token-response! {"expires_in" 60})]
      (t/schedule-refresh-token-before-expiry)
      (is (some? @t/xoauth2-future!))
      (wait-for-two-elements)
      (future-cancel @t/xoauth2-future!)
      (is (= (take elements-to-check @sleep-args) [5000 30000 30000])))))
