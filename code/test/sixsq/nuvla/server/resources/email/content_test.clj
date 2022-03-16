(ns sixsq.nuvla.server.resources.email.content-test
  (:require
    [clojure.java.io :refer [reader writer]]
    [clojure.test :refer [deftest is testing]]
    [sixsq.nuvla.server.resources.email.sending :as sending]
    [sixsq.nuvla.server.resources.email.content :as t]))


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
  (let [plain?     false
        file       "trial-ended-multi"
        f          t/trial-ended
        email-data {:resources (conj resources resource-2 resource-3)}]
    (-> (f (assoc email-data :plain? plain?))
        sending/render-content
        (write (str "test-resources/email/" file "." (if plain? "txt" "html"))))))

(deftest trial-emails
  (testing "trial ending email content should match pre-rendered html"
    (is (= (sending/render-content (t/trial-ending {:days-left 6 :resources resources}))
           (slurp "test-resources/email/trial-ending.html"))))
  (testing "trial end email content should match pre-rendered html"
    (is (= (sending/render-content (t/trial-ended {:resources resources}))
           (slurp "test-resources/email/trial-ended.html"))))
  (testing "trial end email with multiple resources should match pre-rendered html"
    (is (= (sending/render-content (t/trial-ended {:resources (conj resources resource-2 resource-3)}))
           (slurp "test-resources/email/trial-ended-multi.html"))))
  (testing "trial ended email plain content with multiple resources should match pre-rendered text"
    (is (= (sending/render-content (assoc (t/trial-ended
                                            {:resources (conj resources resource-2)})
                                     :plain? true))
           (slurp "test-resources/email/trial-ended-multi.txt"))))
  )
