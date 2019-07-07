(ns sixsq.nuvla.server.resources.email-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [peridot.core :refer :all]
    [postal.core :as postal]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.email :as t]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))

(use-fixtures :once ltu/with-test-server-fixture)

(def base-uri (str p/service-context t/resource-type))

(def md-uri (str p/service-context md/resource-type "/" t/resource-type))

(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["user/test"]})


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest lifecycle
  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")]

    ;; verify resource metadata
    (-> session-anon
        (request md-uri))
    (is (= t/resource-type (-> session-anon
                               (request md-uri)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               :response
                               :body
                               :type-uri)))

    ;; admin query succeeds but is empty
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present :add)
        (ltu/is-operation-absent :delete)
        (ltu/is-operation-absent :edit))

    ;; user query succeeds but is empty
    (-> session-user
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present :add)
        (ltu/is-operation-absent :delete)
        (ltu/is-operation-absent :edit))

    ;; anon query fails
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; anon create must fail
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str {:address "anon@example.com"}))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; check email creation
    (let [admin-uri     (-> session-admin
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str {:address   "admin@example.com"
                                                            :validated true}))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location))
          admin-abs-uri (str p/service-context admin-uri)

          user-uri      (-> session-user
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str {:address   "user@example.com"
                                                            :validated true}))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location))
          user-abs-uri  (str p/service-context user-uri)]

      ;; admin should see 2 email resources
      (-> session-admin
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-resource-uri t/collection-type)
          (ltu/is-count 2))

      ;; user should see only 1
      (-> session-user
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-resource-uri t/collection-type)
          (ltu/is-count 1))

      ;; verify contents of admin email
      (let [email        (-> session-admin
                             (request admin-abs-uri)
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/is-operation-absent :edit)
                             (ltu/is-operation-present :delete)
                             (ltu/is-operation-present :validate)
                             (ltu/body))
            validate-url (->> (u/get-op email :validate)
                              (str p/service-context))]
        (is (= "admin@example.com" (:address email)))
        (is (false? (:validated email)))
        (is validate-url)

        (let [validation-link (atom nil)]
          (with-redefs [email-utils/extract-smtp-cfg
                                            (fn [_] {:host "smtp@example.com"
                                                     :port 465
                                                     :ssl  true
                                                     :user "admin"
                                                     :pass "password"})

                        ;; WARNING: This is a fragile!  Regex matching to recover callback URL.
                        postal/send-message (fn [_ {:keys [body] :as message}]
                                              (let [url (second (re-matches #"(?s).*visit:\n\n\s+(.*?)\n.*" body))]
                                                (reset! validation-link url))
                                              {:code 0, :error :SUCCESS, :message "OK"})]

            (-> session-anon
                (request validate-url)
                (ltu/body->edn)
                (ltu/is-status 202))

            (is (re-matches #"^email/.* successfully validated$"
                            (-> session-anon
                                (request @validation-link)
                                (ltu/body->edn)
                                (ltu/is-status 200)
                                :response
                                :body
                                :message)))

            (is (true? (-> session-admin
                           (request admin-abs-uri)
                           (ltu/body->edn)
                           (ltu/is-status 200)
                           (ltu/is-operation-absent :edit)
                           (ltu/is-operation-present :delete)
                           (ltu/is-operation-absent :validate)
                           :response
                           :body
                           :validated))))))

      ;; verify contents of user email
      (let [email        (-> session-user
                             (request user-abs-uri)
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/is-operation-absent :edit)
                             (ltu/is-operation-present :delete)
                             (ltu/is-operation-present :validate)
                             :response
                             :body)
            validate-url (->> (u/get-op email "validate")
                              (str p/service-context))]
        (is (= "user@example.com" (:address email)))
        (is (false? (:validated email)))
        (is validate-url))

      ;; admin can delete the email
      (-> session-admin
          (request admin-abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; user can delete the email
      (-> session-user
          (request user-abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :put]
                            [resource-uri :options]
                            [resource-uri :post]])))
