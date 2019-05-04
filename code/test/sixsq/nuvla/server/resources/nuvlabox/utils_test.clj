(ns sixsq.nuvla.server.resources.nuvlabox.utils-test
  (:require
    [clojure.test :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.nuvlabox-record :as nb]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as t]
    [peridot.core :refer :all]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context nb/resource-type))


(deftest get-vpn-url
  (is (= "http://10.10.10.10:8080") (t/get-url "10.10.10.10" 8080))
  (is (= "https://10.10.10.10:8081") (t/get-url "10.10.10.11" 8081 true))
  (is (nil? (t/get-url nil 8080)))
  (is (nil? (t/get-url "10.10.10.10" nil))))


(deftest check-is-nano?
  (are [expected arg] (= expected (t/is-nano? arg))
                      false {}
                      false nil
                      false {:formFactor nil}
                      false {:formFactor "other"}
                      true {:formFactor "nano"}
                      true {:formFactor "Nano"}
                      true {:formFactor "NANO"}))

