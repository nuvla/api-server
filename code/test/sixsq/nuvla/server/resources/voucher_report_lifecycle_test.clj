(ns sixsq.nuvla.server.resources.voucher-report-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.voucher-report :as t]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def valid-acl-admin {:owners    ["group/nuvla-admin"]
                      :view-data ["group/nuvla-user"]})

(def valid-acl-user {:owners    ["user/abcdef01-abcd-abcd-abcd-abcdef012346"]
                     :view-data ["group/nuvla-user"]})


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest lifecycle
  (let [session-anon               (-> (ltu/ring-app)
                                       session
                                       (content-type "application/json"))
        session-admin              (header session-anon authn-info-header "user/abcdef01-abcd-abcd-abcd-abcdef012347 group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user               (header session-anon authn-info-header "user/abcdef01-abcd-abcd-abcd-abcdef012346 group/nuvla-user group/nuvla-anon")

        valid-voucher-report-admin {:name         "my-voucher-report"
                                    :description  "my-voucher-report description"

                                    :supplier     "user/abcdef01-abcd-abcd-abcd-abcdef012345"
                                    :parent       "voucher/abcdef01-abcd-abcd-abcd-abcdef012346"
                                    :amount-spent 50.0

                                    :acl          valid-acl-admin
                                    }

        valid-voucher-report-user  (assoc valid-voucher-report-admin :acl valid-acl-user)]

    ;; admin/user query succeeds but is empty
    (doseq [session [session-admin session-user]]
      (-> session
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?)))

    ;; anon query fails
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; anon create must fail
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-voucher-report-admin))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; check voucher-report creation
    (let [admin-uri     (-> session-admin
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str valid-voucher-report-admin))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location))

          admin-abs-uri (str p/service-context admin-uri)

          user-uri      (-> session-user
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str valid-voucher-report-user))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location))

          user-abs-uri  (str p/service-context user-uri)]

      ;; admin should see 2 voucher-report resources
      (-> session-admin
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-resource-uri t/collection-type)
          (ltu/is-count 2))

      ;; user also sees 2 cause of acls
      (-> session-user
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-resource-uri t/collection-type)
          (ltu/is-count 2))

      ;; verify contents of admin voucher-report
      (let [voucher-report-full (-> session-admin
                                    (request admin-abs-uri)
                                    (ltu/body->edn)
                                    (ltu/is-status 200)
                                    (ltu/is-operation-present :edit)
                                    (ltu/is-operation-present :delete))
            voucher-report      (:body (:response voucher-report-full))]

        (is (= "my-voucher-report" (:name voucher-report)))

        ;; verify that an edit works
        (let [updated (assoc voucher-report :amount-spent 60.50)]

          (-> session-admin
              (request admin-abs-uri
                       :request-method :put
                       :body (json/write-str updated))
              (ltu/body->edn)
              (ltu/is-status 200)
              :response
              :body)

          (let [updated-body (-> session-admin
                                 (request admin-abs-uri)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 :response
                                 :body)]

            (is (= 60.50 (:amount-spent updated-body))))))

      ;; admin can delete the voucher-report
      (-> session-admin
          (request admin-abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; user cannot delete the voucher-report
      (-> session-user
          (request user-abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
