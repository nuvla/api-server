(ns sixsq.nuvla.server.resources.email.text-test
  (:require
    [clojure.java.io :refer [reader writer]]
    [clojure.test :refer [deftest is testing]]
    [sixsq.nuvla.server.resources.email.text :as t]
    [sixsq.nuvla.server.resources.email.utils :as u]))


(def resource-1 {:id    "61467019"
                 :url   "https://nuvla.io/ui/deployment/61467019-2931-463d-b32c-c60a73c205e5"
                 :title "Nginx simple app"
                 :kind  "Deployment"})

(def resource-2 {:id    "71467019"
                 :url   "https://nuvla.io/ui/edge/71467019-2931-463d-b32c-c60a73c205e5"
                 :title "My first NuvlaBox"
                 :kind  "NuvlaBox"})

(def resources [resource-1])


(deftest trial-emails
  (testing "trail ending email content should match fixed end result"
    (is (= (u/render-email
             (t/trial-ending {:days-left 6
                              :resources resources}))
           (slurp "test-resources/email/trial-ending.html"))))
  (testing "trail end email content should match fixed end result"
    (is (= (u/render-email (t/trial-ended {:resources resources}))
           (slurp "test-resources/email/trial-ended.html"))))

  (testing "trail end email with more than one resource email"
    (is (= (u/render-email (t/trial-ended {:resources (conj resources resource-2)}))
           (slurp "test-resources/email/trial-ended-multi.html")))))

(comment
  ; use this tool to create the end result for the test
  (defn write [input location]
    (with-open [w (writer location)]
      (.write w input)))

  (-> #_(t/trial-ending {:days-left 6
                         :resources resources})
    (t/trial-ended {:resources (conj resources resource-2)})
    u/render-email
    (write "test-resources/email/trial-ended-multi.html")))

