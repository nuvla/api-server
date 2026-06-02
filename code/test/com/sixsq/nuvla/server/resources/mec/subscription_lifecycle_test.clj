 (ns com.sixsq.nuvla.server.resources.mec.subscription-lifecycle-test
   (:require
     [cheshire.core :as json]
     [clojure.test :refer [deftest is testing use-fixtures]]
     [com.sixsq.nuvla.server.app.params :as p]
     [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
     [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
     [peridot.core :refer [content-type header request session]]))

 (use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context "mec/mm1/app_lcm/v1"))

 (deftest subscription-lifecycle
   (let [session-user-1 (-> (ltu/ring-app)
                            session
                            (content-type "application/json")
                            (header authn-info-header "user/alice user/alice group/nuvla-user group/nuvla-anon"))
         session-user-2 (-> (ltu/ring-app)
                            session
                            (content-type "application/json")
                            (header authn-info-header "user/bob user/bob group/nuvla-user group/nuvla-anon"))]
     (testing "durable subscription CRUD is owner-scoped"
      (let [create-body {:subscriptionType "AppInstanceStateChange"
                          :callbackUri "https://example.org/webhooks/alice"
                          :appInstanceFilter {:app-name "demo-app"}}
             create-response (-> session-user-1
                                 (request (str base-uri "/subscriptions")
                                          :request-method :post
                                          :body (json/generate-string create-body))
                                 :response)
             created (when (:body create-response)
                       (json/parse-string (:body create-response) true))
             sub-id (:id created)]

         (is (= 201 (:status create-response)) (pr-str created))
        (is (= "AppInstanceStateChange" (:subscriptionType created)))
         (is (= "user/alice" (:owner created)))
         (is (.startsWith sub-id "subscription/"))

         (when sub-id
           (let [list-user-1 (-> session-user-1
                                 (request (str base-uri "/subscriptions")
                                          :request-method :get)
                                 :response)
                 list-user-1-body (json/parse-string (:body list-user-1) true)
                 list-user-2 (-> session-user-2
                                 (request (str base-uri "/subscriptions")
                                          :request-method :get)
                                 :response)
                 list-user-2-body (json/parse-string (:body list-user-2) true)
                 get-user-1 (-> session-user-1
                                (request (str base-uri "/subscriptions/" sub-id)
                                         :request-method :get)
                                :response)
                 get-user-2 (-> session-user-2
                                (request (str base-uri "/subscriptions/" sub-id)
                                         :request-method :get)
                                :response)
                 delete-user-1 (-> session-user-1
                                   (request (str base-uri "/subscriptions/" sub-id)
                                            :request-method :delete)
                                   :response)
                 get-after-delete (-> session-user-1
                                      (request (str base-uri "/subscriptions/" sub-id)
                                               :request-method :get)
                                      :response)]

             (is (= 200 (:status list-user-1)))
             (is (some #(= sub-id (:id %)) (:items list-user-1-body)) (pr-str list-user-1-body))

             (is (= 200 (:status list-user-2)))
             (is (not-any? #(= sub-id (:id %)) (:items list-user-2-body)) (pr-str list-user-2-body))

             (is (= 200 (:status get-user-1)))
             (is (= 403 (:status get-user-2)))

             (is (= 204 (:status delete-user-1)))
             (is (= 404 (:status get-after-delete)))))))))
