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



(deftest list-all-identifiers-for-series
  (is (empty? (t/list-all-identifiers-for-series t/default-serie))))


(deftest already-used-identifiers
  (is (empty? (t/already-used-identifiers))))


(deftest random-choose-remaining-identifier
  (is (nil? (t/random-choose-remaining-identifier t/default-serie)))

  (with-redefs [t/list-all-identifiers-for-series (fn [_] (map #(str "my-identifier-" %) (range 10)))]
    (is (string? (t/random-choose-remaining-identifier t/default-serie))))

  ;;when all possible identifiers are already taken
  (with-redefs [t/list-all-identifiers-for-series (fn [_] (map #(str "my-identifier-" %) (range 10)))
                t/already-used-identifiers (fn [] (map #(str "my-identifier-" %) (range 10)))]
    (is (nil? (t/random-choose-remaining-identifier t/default-serie)))))


(deftest isNano?
  (are [expected arg] (= expected (t/isNano? arg))
                      false {}
                      false nil
                      false {:formFactor nil}
                      false {:formFactor "other"}
                      true {:formFactor "nano"}
                      true {:formFactor "Nano"}
                      true {:formFactor "NANO"}))

(deftest addIdentifier
  (with-redefs [t/list-all-identifiers-for-series (fn [_] (list "an-identifier"))]
    (are [expected arg] (= expected (t/add-identifier arg))
                        nil nil
                        {} {}
                        {:missing "formFactor"} {:missing "formFactor"}
                        {:formFactor "other"} {:formFactor "other"}
                        {:formFactor "other" :identifier "42"} {:formFactor "other" :identifier "42"}
                        {:formFactor "nano" :identifier "an-identifier" :name "an-identifier"} {:formFactor "nano"}
                        {:formFactor "NANO" :identifier "an-identifier" :name "an-identifier"} {:formFactor "NANO"}
                        {:formFactor "nano" :identifier "an-identifier" :name "an-identifier"} {:formFactor "nano" :identifier "will-be-replaced"})))
