(ns sixsq.nuvla.server.resources.email.text-test
  (:require
    [clojure.java.io :refer [reader writer]]
    [clojure.test :refer [deftest is testing]]
    [sixsq.nuvla.server.resources.email.text :as t]
    [sixsq.nuvla.server.resources.email.utils :as u]))

(defn write [input location]
  (let [wrtr (writer location)]
    (.write wrtr input)
    (.close wrtr)))

(defn email-render [email-data]
  (-> email-data
      u/email-render
      :body
      last
      :content))

(def resource-1 {:id    "61467019"
                 :url   "https://nuvla.io/ui/deployment/61467019-2931-463d-b32c-c60a73c205e5"
                 :title "Nginx simple app"
                 :kind  "Deployment"})

(def resource-2 {:id    "71467019"
                 :url   "https://nuvla.io/ui/edge/71467019-2931-463d-b32c-c60a73c205e5"
                 :title "My first NuvlaBox"
                 :kind  "NuvlaBox"})

(def resources [resource-1])

(comment
  ; use this tool to create the end result for the test
  (-> #_(t/trial-ending {:trial-days-left 6
                         :resources       resources})
    (t/trial-ended {:resources (conj resources resource-2)})

    u/email-render
    :body
    last
    :content
    (write "test-resources/email/trial-ended-multi.html")))

(deftest trial-emails
  (testing "trail ending email content should match fixed end result"
    (is (= (email-render (t/trial-ending {:trial-days-left 6
                                          :resources       resources}))
           (slurp "test-resources/email/trial-ending.html"))))
  (testing "trail end email content should match fixed end result"
    (is (= (email-render (t/trial-ended {:resources resources}))
           (slurp "test-resources/email/trial-ended.html"))))

  (testing "trail end email with more than one resource email"
    (is (= (email-render (t/trial-ended {:resources (conj resources resource-2)}))
           (slurp "test-resources/email/trial-ended-multi.html")))))
