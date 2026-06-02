 (ns com.sixsq.nuvla.server.resources.mec.app-package-subscription-test
   "Tests for MEC application package subscriptions."
   (:require
     [clojure.string :as str]
     [clojure.test :refer [deftest is testing]]
     [com.sixsq.nuvla.server.resources.mec.app-package-subscription :as subscription]))
 
 (def test-user-id "user/test-user")
 
 (def test-app-package
   {:app-pkg-id        "module/test-123"
    :app-d-id          "module/test-123"
    :app-name          "test-app"
    :operational-state "ENABLED"
    :onboarding-state  "ONBOARDED"})
 
 (deftest test-create-package-subscription
   (testing "Create onboarding package subscription"
     (let [sub (subscription/create-subscription
                 "AppPackageOnBoardingNotification"
                 "https://example.com/webhook"
                 {:app-name "test-app"}
                 test-user-id
                 "/mec/mm3/app_pkgm/v1")]
       (is (string? (:id sub)))
       (is (.startsWith (:id sub) "subscription/"))
       (is (= "AppPackageOnBoardingNotification" (:subscription-type sub)))
       (is (= "https://example.com/webhook" (:callback-uri sub)))
       (is (= {:app-name "test-app"} (:app-pkg-filter sub)))
       (is (= "/mec/mm3/app_pkgm/v1" (:api-base-path sub)))
       (is (= test-user-id (:owner sub)))
       (is (true? (:active sub))))))
 
 (deftest test-validate-package-subscription
   (testing "Reject invalid callback URI"
     (let [sub {:id                "subscription/test"
                :subscription-type "AppPackageOnBoardingNotification"
                :callback-uri      "invalid"
                :owner             test-user-id
                :active            true}
           result (subscription/validate-subscription sub)]
       (is (false? (:valid? result)))
       (is (some? (:errors result))))))
 
 (deftest test-matches-package-filter
   (testing "Filter matches expected application package"
     (let [sub (subscription/create-subscription
                 "AppPackageStateChangeNotification"
                 "https://example.com/webhook"
                 {:app-pkg-id "module/test-123"
                  :operational-state "ENABLED"}
                 test-user-id
                 "/mec/mm1/app_pkgm/v1")]
       (is (true? (subscription/matches-app-pkg-filter? sub test-app-package)))))
 
   (testing "Filter mismatch is rejected"
     (let [sub (subscription/create-subscription
                 "AppPackageStateChangeNotification"
                 "https://example.com/webhook"
                 {:app-name "other-app"}
                 test-user-id
                 "/mec/mm1/app_pkgm/v1")]
       (is (false? (subscription/matches-app-pkg-filter? sub test-app-package))))))
 
 (deftest test-build-package-notifications
   (testing "Onboarding notifications use the subscription route context"
     (let [sub (subscription/create-subscription
                 "AppPackageOnBoardingNotification"
                 "https://example.com/webhook"
                 {}
                 test-user-id
                 "/mec/mm3/app_pkgm/v1")
           notification (subscription/build-app-package-onboarding-notification sub test-app-package)]
       (is (= "AppPackageOnBoardingNotification" (:notification-type notification)))
       (is (= (:id sub) (:subscription-id notification)))
       (is (= "module/test-123" (:app-pkg-id notification)))
       (is (str/starts-with? (get-in notification [:_links :subscription :href])
                             "/mec/mm3/app_pkgm/v1/subscriptions/subscription/"))
       (is (= "/mec/mm3/app_pkgm/v1/app_packages/module/test-123"
              (get-in notification [:_links :app-package :href])))))
 
   (testing "State-change notifications keep change metadata"
     (let [sub (subscription/create-subscription
                 "AppPackageStateChangeNotification"
                 "https://example.com/webhook"
                 {}
                 test-user-id
                 "/mec/mm1/app_pkgm/v1")
           notification (subscription/build-app-package-state-change-notification
                          sub
                          test-app-package
                          "DELETION"
                          "ENABLED")]
       (is (= "AppPackageStateChangeNotification" (:notification-type notification)))
       (is (= "DELETION" (:change-type notification)))
       (is (= "ENABLED" (:previous-state notification))))))
