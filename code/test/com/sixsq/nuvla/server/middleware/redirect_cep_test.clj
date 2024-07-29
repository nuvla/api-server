(ns com.sixsq.nuvla.server.middleware.redirect-cep-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.sixsq.nuvla.server.middleware.redirect-cep :as t]))


(def handler (t/redirect-cep identity))


(deftest check-responses
  (let [response (handler {:uri "/"})]
    (is (= 302 (:status response)))
    (is (= "/api/cloud-entry-point" (get-in response [:headers "Location"]))))

  (let [response (handler {:uri "/api"})]
    (is (= 302 (:status response)))
    (is (= "/api/cloud-entry-point" (get-in response [:headers "Location"]))))

  (let [response (handler {:uri "/api/"})]
    (is (= 302 (:status response)))
    (is (= "/api/cloud-entry-point" (get-in response [:headers "Location"]))))

  (let [response (handler {:uri "/apix"})]
    (is (= 302 (:status response)))
    (is (= "/api/cloud-entry-point" (get-in response [:headers "Location"]))))

  (let [response (handler {:uri "/api/some-other-resource"})]
    (is (= "/api/some-other-resource" (:uri response)))))
