(ns sixsq.nuvla.server.resources.email.text-test
  (:require
    [clojure.java.io :refer [writer reader resource]]
    [clojure.test :refer :all]
    [sixsq.nuvla.server.resources.email.text :as t]
    [sixsq.nuvla.server.resources.email.utils :as u]))

(defn write [input location]
  (let [wrtr (writer location)]
    (.write wrtr input)
    (.close wrtr)))

(comment
  (-> #_(t/trial-ending {:trial-days-left 5})
      (t/trial-ended true )

      u/email-render
      :body 
      last
     :content
      (write "test-resources/email-trial-ended.html")))
