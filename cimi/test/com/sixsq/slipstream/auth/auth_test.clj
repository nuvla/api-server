(ns com.sixsq.slipstream.auth.auth-test
  (:require
    [clojure.test :refer :all]
    [com.sixsq.slipstream.auth.auth :as auth]
    [com.sixsq.slipstream.auth.test-helper :as th]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]))

(use-fixtures :each ltu/with-test-server-fixture)

(def valid-credentials {:username "super"
                        :password "supeRsupeR"
                        :emailAddress "jane@example.org"
                        :firstName "Jane"
                        :lastName "Tester"})
(def valid-request {:params (merge {:authn-method :internal} valid-credentials)})

(deftest test-auth-internal-accepts-username-and-fallbacks-to-user-name
  (th/add-user-for-test! valid-credentials)
  (is (= 200 (:status (auth/login valid-request))))
  (is (= 200 (:status (auth/login {:params {:authn-method :internal :username "super" :password "supeRsupeR"}}))))
  (is (= 200 (:status (auth/login {:params {:authn-method :internal :user-name "super" :password "supeRsupeR"}}))))

  (is (= 401 (:status (auth/login {:params {:authn-method :internal
                                            :username "wrong" :user-name "super"
                                            :password "supeRsupeR"}}))))
  (is (= 200 (:status (auth/login {:params {:authn-method :internal
                                            :username "super" :user-name "wrong"
                                            :password "supeRsupeR"}})))))

(deftest test-auth-internal-invalid-credentials
  (th/add-user-for-test! valid-credentials)
  (is (= 401 (:status (auth/login {:params {:authn-method :internal}}))))
  (is (= 401 (:status (auth/login {:params {:authn-method :internal :username "super" :password "wrong"}})))))

(deftest test-auth-internal-valid-credentials
  (th/add-user-for-test! valid-credentials)
  (is (= 200 (:status (auth/login valid-request))))
  (is (get-in (auth/login valid-request) [:cookies "com.sixsq.slipstream.cookie" :value])))

(deftest test-auth-logout
  (let [logout-response (auth/logout valid-request)
        cookies         (get-in logout-response [:cookies "com.sixsq.slipstream.cookie"])]
    (is (= 200 (:status logout-response)))
    (is (= "INVALID" (:value cookies)))
    (is (zero? (:max-age cookies)))))
