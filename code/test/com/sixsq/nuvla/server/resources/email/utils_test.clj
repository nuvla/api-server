(ns com.sixsq.nuvla.server.resources.email.utils-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.sixsq.nuvla.server.resources.email.sending :as sending]
    [com.sixsq.nuvla.server.resources.email.utils :as t]))

(deftest send-msg
  (let [result (atom nil)]
    (with-redefs [sending/dispatch (fn [_nuvla-config email-data] (reset! result email-data))]
     (t/send-email "some-address" (fn [_ {:keys [foo]}] (str "subject " foo)) (fn [_ {:keys [foo]}] (str "body " foo)) {:foo "bar"})
      (is (= {:body    "body bar"
              :from    "administrator"
              :subject "subject bar"
              :to      ["some-address"]} @result)))))

(deftest send-group-invitation-accepted
  (is (= {:content "Your invitation to group group/x has been accepted by invited-email@example.com."
          :type    "text/plain"}
         (second (t/group-invitation-email-body {} {:group "group/x" :invited-email "invited-email@example.com"})))))
