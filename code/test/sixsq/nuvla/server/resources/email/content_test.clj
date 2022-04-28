(ns sixsq.nuvla.server.resources.email.content-test
  (:require
    [clojure.java.io :refer [writer]]
    [clojure.test :refer [deftest is testing]]
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

(def resource-3 {:id    "81467019"
                 :url   "https://nuvla.io/ui/deployment/81467019-2931-463d-b32c-c60a73c205e5"
                 :kind  "Deployment"})

(def resources [resource-1])

(comment
  ; use this tool to create the end result for the test
  (defn write [input location]
    (with-open [w (writer location)]
      (.write w input)))

  ; use this tool to create the end result html or txt
  (doseq [{:keys [plain? file f email-data]} [{:file "trial-ending" 
                                               :email-data {:days-left 6 :resources resources} 
                                               :f t/trial-ending}
                                              {:file "trial-ended" 
                                               :email-data {:resources resources} 
                                               :f t/trial-ended}
                                              {:file "trial-ended-multi" 
                                               :email-data {:resources (conj resources resource-2 resource-3)} 
                                               :f t/trial-ended}
                                              {:file "trial-ended-multi" 
                                               :email-data {:resources (conj resources resource-2)} 
                                               :f t/trial-ended
                                               :plain? true}
                                              {:file "trial-ended-with-payment" 
                                               :email-data {:resources resources} 
                                               :f t/trial-ended-with-payment}]]
    (-> (f email-data)
        (assoc :plain? plain?)
        sending/render-content
        (write (str "test-resources/email/" file "." (if plain? "txt" "html"))))))

(deftest email-content
  (testing "trial ending email content should match pre-rendered html"
    (is (= (sending/render-content (t/trial-ending {:days-left 6 :resources resources}))
           (slurp "test-resources/email/trial-ending.html"))))
  (testing "trial end email content should match pre-rendered html"
    (is (= (sending/render-content (t/trial-ended {:resources resources}))
           (slurp "test-resources/email/trial-ended.html"))))
  (testing "trial end email with multiple resources should match pre-rendered html"
    (is (= (sending/render-content (t/trial-ended {:resources (conj resources resource-2 resource-3)}))
           (slurp "test-resources/email/trial-ended-multi.html"))))
  (testing "trial ended email plain content with multiple resources should match pre-rendered txt"
    (is (= (sending/render-content (assoc (t/trial-ended
                                            {:resources (conj resources resource-2)})
                                     :plain? true))
           (slurp "test-resources/email/trial-ended-multi.txt"))))
  (testing "trial end with payment email content should match pre-rendered html"
    (is (= (sending/render-content (t/trial-ended-with-payment {:resources resources}))
           (slurp "test-resources/email/trial-ended-with-payment.html")))))
