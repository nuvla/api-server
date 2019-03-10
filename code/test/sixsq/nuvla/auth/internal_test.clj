(ns sixsq.nuvla.auth.internal-test
  (:require
    [clojure.test :refer :all]
    [sixsq.nuvla.auth.internal :as t]
    [sixsq.nuvla.auth.test-helper :as th]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def super (ltu/random-string "super-"))


(def jane (ltu/random-string "jane-"))


(def valid-creds-super {:username super :password (ltu/random-string)})


(def valid-creds-jane {:username jane :password (ltu/random-string)})


(def invalid-creds [{:username super :password (ltu/random-string)}
                    {:username super}
                    {:username jane :password (ltu/random-string)}
                    {:username jane}
                    {:username (ltu/random-string "WRONG-") :password (ltu/random-string)}
                    {:username (ltu/random-string "WRONG-")}
                    {}])


(deftest test-password-hashing
  (is (nil? (t/hash-password nil)))
  (is (= "304D73B9607B5DFD48EAC663544F8363B8A03CAAD6ACE21B369771E3A0744AAD0773640402261BD5F5C7427EF34CC76A2626817253C94D3B03C5C41D88C64399"
         (t/hash-password "supeRsupeR"))))


(deftest check-valid?
  (th/add-user-for-test! valid-creds-super)
  (th/add-user-for-test! valid-creds-jane)

  (is (t/valid? valid-creds-super))
  (is (t/valid? valid-creds-jane))
  (doseq [wrong invalid-creds]
    (is (not (t/valid? wrong)))))


(deftest check-create-claims
  (th/add-user-for-test! (merge valid-creds-jane {:isSuperUser false}))
  (is (= {:username jane
          :roles    "USER ANON"}
         (t/create-claims jane)))

  ; FIXME: it's not possible to create a super user with auto template.
  #_(th/add-user-for-test! (merge valid-creds-super {:isSuperUser true}))
  #_(is (= {:username "super"
            :roles    "ADMIN USER ANON"}
           (t/create-claims "super"))))


(deftest check-login
  (th/add-user-for-test! (merge valid-creds-super {:isSuperUser true}))
  (th/add-user-for-test! (merge valid-creds-jane {:isSuperUser false}))

  (let [response (t/login {:params valid-creds-super})]
    (is (= 200 (:status response)))
    (is (get-in response [:cookies "com.sixsq.nuvla.cookie" :value])))

  ;; FIXME: This should really return 403.
  (let [response (t/login {:params {:username "super" :password "WRONG"}})]
    (is (= 401 (:status response)))
    (is (nil? (get-in response [:cookies "com.sixsq.nuvla.cookie" :value]))))

  (let [response (t/login {:params valid-creds-jane})]
    (is (= 200 (:status response)))
    (is (get-in response [:cookies "com.sixsq.nuvla.cookie" :value])))

  ;; FIXME: This should really return 403.
  (let [response (t/login {:params {:username "jane" :password "WRONG"}})]
    (is (= 401 (:status response)))
    (is (nil? (get-in response [:cookies "com.sixsq.nuvla.cookie" :value])))))


(deftest check-logout
  (let [response (t/logout)]
    (is (= 200 (:status response)))
    (is (= "INVALID" (get-in response [:cookies "com.sixsq.nuvla.cookie" :value])))))
