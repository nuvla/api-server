(ns sixsq.nuvla.server.resources.vendor-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer [deftest is use-fixtures]]
    [clojure.tools.logging :as log]
    [environ.core :as env]
    [peridot.core :refer [content-type header request session]]
    [ring.util.codec :as codec]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.callback-vendor :as callback-vendor]
    [sixsq.nuvla.server.resources.common.user-utils-test :as user-utils-test]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.vendor :as t]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture
              (partial user-utils-test/with-existing-user "tarzan@example.com"))


(def base-uri (str p/service-context t/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest lifecycle
  (if-not (env/env :stripe-api-key)
    (log/error "vendor lifecycle is not tested because lack of stripe-api-key!")
    (let [session-anon  (-> (session (ltu/ring-app))
                            (content-type "application/json"))
          session-admin (header session-anon authn-info-header
                                "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
          session-user  (header session-anon authn-info-header (str @user-utils-test/user-id! " group/nuvla-user group/nuvla-anon"))

          account-id    "some-account-id"]


      ;; create: NOK for anon
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str {}))
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; check error linked to state
      (let [callback-url (-> session-user
                             (request base-uri
                                      :request-method :post
                                      :body (json/write-str {}))
                             (ltu/body->edn)
                             (ltu/is-status 303)
                             (ltu/location)
                             (codec/url-decode)
                             (->> (re-find #".*redirect_uri=(.*)/execute$"))
                             second)]

        (-> session-anon
            (request callback-url)
            (ltu/body->edn)
            (ltu/is-status 403))

        (-> session-anon
            (request (str callback-url "/execute" (format "?state=%s&code=%s" "fake-state" "some-code"))
                     :request-method :post)
            (ltu/body->edn)
            (ltu/message-matches #"Incorrect state parameter!"))

        (-> session-anon
            (request (str callback-url "/execute" (format "?state=%s&code=%s" "fake-state2" "some-code"))
                     :request-method :post)
            (ltu/body->edn)
            (ltu/message-matches #"cannot re-execute callback")))

      ;; check error cases linked to unknown authorization code
      (let [callback-url (-> session-user
                             (request base-uri
                                      :request-method :post
                                      :body (json/write-str {}))
                             (ltu/body->edn)
                             (ltu/is-status 303)
                             (ltu/location)
                             (codec/url-decode)
                             (->> (re-find #".*redirect_uri=(.*)/execute$"))
                             second)
            state        (-> session-admin
                             (request callback-url)
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (get-in [:response :body :data :state]))]

        (-> session-anon
            (request (str callback-url "/execute" (format "?state=%s&code=%s" state "some-code"))
                     :request-method :post)
            (ltu/body->edn)
            (ltu/message-matches #"Authorization code does not exist.*")))


      ;; check valid callback call without redirect
      (with-redefs [callback-vendor/get-account-id (constantly account-id)]
        (let [callback-url (-> session-user
                               (request base-uri
                                        :request-method :post
                                        :body (json/write-str {}))
                               (ltu/body->edn)
                               (ltu/is-status 303)
                               (ltu/location)
                               (codec/url-decode)
                               (->> (re-find #".*redirect_uri=(.*)/execute$"))
                               second)
              state        (-> session-admin
                               (request callback-url)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (get-in [:response :body :data :state]))
              vendor-url   (-> session-anon
                               (request (str callback-url "/execute" (format "?state=%s&code=%s" state "some-code"))
                                        :request-method :post)
                               (ltu/body->edn)
                               (ltu/is-status 201)
                               (ltu/location-url))]

          ;; user can't create two account for an active-claim
          (-> session-user
              (request base-uri
                       :request-method :post
                       :body (json/write-str {}))
              (ltu/body->edn)
              (ltu/is-status 400)
              (ltu/message-matches #"Vendor already exist!"))

          (is (= (-> session-user
                     (request vendor-url)
                     (ltu/body->edn)
                     (ltu/is-status 200)
                     :response
                     :body
                     (dissoc :created :updated :created-by))
                 {:parent        @user-utils-test/user-id!,
                  :account-id    account-id,
                  :acl
                                 {:view-acl  [@user-utils-test/user-id!,],
                                  :view-meta [@user-utils-test/user-id!,],
                                  :view-data [@user-utils-test/user-id!,],
                                  :owners    ["group/nuvla-admin"]},
                  :id            (str "vendor/" (str/replace @user-utils-test/user-id! "/" "-")),
                  :resource-type "vendor"}))

          (-> session-admin
              (request vendor-url :request-method :delete)
              (ltu/body->edn)
              (ltu/is-status 200))))


      (let [ui-redirect "https://ui-defined-redirect"]
        (with-redefs [callback-vendor/get-account-id (constantly account-id)]
          (let [callback-url (-> session-user
                                 (request base-uri
                                          :request-method :post
                                          :body (json/write-str {:redirect-url ui-redirect}))
                                 (ltu/body->edn)
                                 (ltu/is-status 303)
                                 (ltu/location)
                                 (codec/url-decode)
                                 (->> (re-find #".*redirect_uri=(.*)/execute$"))
                                 second)
                state        (-> session-admin
                                 (request callback-url)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (get-in [:response :body :data :state]))]

            (is (= ui-redirect
                   (-> session-anon
                       (request (str callback-url "/execute"
                                     (format "?state=%s&code=%s" state "some-code"))
                                :request-method :post)
                       (ltu/body->edn)
                       (ltu/is-status 303)
                       (ltu/location))))))))))



(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))



