(ns sixsq.nuvla.server.resources.email.text-test
  (:require
    [clojure.java.io :refer [writer reader]]
    [clojure.string :as str]
    [clojure.test :refer [deftest testing is]]
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

(def resources [{:id    "873ab926"
                 :url   "/var/folders/qc/4xjzgdw17z32fkb3x36nrv6m0000gp/T/clojure-8514830771576825996.edn"
                 :title "Nginx simple app"
                 :kind  "Deployment"}])

(comment
  ; use this tool to create the end result for the test
  (-> #_(t/trial-ending {:trial-days-left 6
                       :resources resources})
      (t/trial-ended {:resources resources})

      u/email-render
      :body 
      last
      :content
      (write "test-resources/email-trial-ended.html")))

(deftest trial-emails
  (testing "trail ending email content should match fixed end result"
    (is (= (email-render (t/trial-ending {:trial-days-left 6
                                          :resources resources})) 
           (slurp  "test-resources/email-trial-ending.html"))))
  (testing "trail end email content should match fixed end result"
    (is (= (email-render (t/trial-ended {:resources resources})) 
           (slurp  "test-resources/email-trial-ended.html")))))
