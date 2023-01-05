(ns sixsq.nuvla.server.resources.email.content-test
  (:require
    [clojure.java.io :refer [writer]]
    [clojure.test :refer [deftest is testing]]
    [java-time :as time]
    [sixsq.nuvla.server.resources.email.content :as t]
    [sixsq.nuvla.server.resources.email.sending :as sending]))


(def resource-1 {:id    "61467019"
                 :url   "https://nuvla.io/ui/deployment/61467019-2931-463d-b32c-c60a73c205e5"
                 :title "Nginx simple app"
                 :kind  "Deployment"})

(def resource-2 {:id    "71467019"
                 :url   "https://nuvla.io/ui/edge/71467019-2931-463d-b32c-c60a73c205e5"
                 :title "My first NuvlaBox"
                 :kind  "NuvlaBox"})

(def resource-3 {:id   "81467019"
                 :url  "https://nuvla.io/ui/deployment/81467019-2931-463d-b32c-c60a73c205e5"
                 :kind "Deployment"})

(def resources [resource-1])

(comment
  ; use this tool to create the end result html or txt
  (let [write (fn [input location]
                (with-open [w (writer location)]
                  (.write w input)))]
      (doseq [{:keys [plain? file f email-data]} [{:file       "trial-ending"
                                                   :email-data {:days-left 6 :resources resources}
                                                   :f          t/trial-ending}
                                                  {:file       "trial-ended"
                                                   :email-data {:resources resources}
                                                   :f          t/trial-ended}
                                                  {:file       "trial-ended-multi"
                                                   :email-data {:resources (conj resources resource-2 resource-3)}
                                                   :f          t/trial-ended}
                                                  {:file       "trial-ended-multi"
                                                   :email-data {:resources (conj resources resource-2)}
                                                   :f          t/trial-ended
                                                   :plain?     true}
                                                  {:file       "trial-ended-with-payment"
                                                   :email-data {:resources resources}
                                                   :f          t/trial-ended-with-payment}
                                                  {:file       "coupon-ending"
                                                   :email-data {:days-left 5}
                                                   :f          t/coupon-ending}
                                                  {:file       "coupon-ended"
                                                   :email-data {}
                                                   :f          t/coupon-ended}]]
        (-> (f email-data)
            (assoc :plain? plain?
                   :header-img "https://nuvla.io/img/email/header-2.png")
            sending/render-content
            (write (str "test-resources/email/" file "." (if plain? "txt" "html")))))))

(deftest email-content
  (with-redefs [time/java-date (constantly #inst "2022-01-05T12:07:30.628-00:00")]
    (testing "trial ending email content should match pre-rendered html"
      (is (= (slurp "test-resources/email/trial-ending.html")
             (sending/render-content (assoc
                                       (t/trial-ending {:days-left 6 :resources resources})
                                       :header-img "https://nuvla.io/img/email/header-2.png")))))
    (testing "trial end email content should match pre-rendered html"
      (is (= (slurp "test-resources/email/trial-ended.html")
             (sending/render-content (assoc (t/trial-ended {:resources resources})
                                       :header-img "https://nuvla.io/img/email/header-2.png")))))
    (testing "trial end email with multiple resources should match pre-rendered html"
      (is (= (slurp "test-resources/email/trial-ended-multi.html")
             (sending/render-content (assoc (t/trial-ended {:resources (conj resources resource-2 resource-3)})
                                       :header-img "https://nuvla.io/img/email/header-2.png")))))
    (testing "trial ended email plain content with multiple resources should match pre-rendered txt"
      (is (= (slurp "test-resources/email/trial-ended-multi.txt")
             (sending/render-content (assoc (t/trial-ended {:resources (conj resources resource-2)})
                                       :plain? true)))))
    (testing "trial end with payment email content should match pre-rendered html"
      (is (= (slurp "test-resources/email/trial-ended-with-payment.html")
             (sending/render-content (assoc (t/trial-ended-with-payment {:resources resources})
                                       :header-img "https://nuvla.io/img/email/header-2.png")))))
    (testing "coupon ending content should match pre-rendered html"
      (is (= (slurp "test-resources/email/coupon-ending.html")
             (sending/render-content (assoc (t/coupon-ending {:days-left 5})
                                       :header-img "https://nuvla.io/img/email/header-2.png")))))
    (testing "coupon ended content should match pre-rendered html"
      (is (= (slurp "test-resources/email/coupon-ended.html")
             (sending/render-content (assoc (t/coupon-ended {})
                                       :header-img "https://nuvla.io/img/email/header-2.png")))))))
