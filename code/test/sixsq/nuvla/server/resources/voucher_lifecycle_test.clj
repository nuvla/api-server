(ns sixsq.nuvla.server.resources.voucher-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.voucher :as t]
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
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "user/abcdef01-abcd-abcd-abcd-abcdef012347 group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user (header session-anon authn-info-header "user/abcdef01-abcd-abcd-abcd-abcdef012346 group/nuvla-user group/nuvla-anon")

        valid-voucher-admin {:name            "my-voucher"
                             :description     "my-voucher description"

                             :owner           "user/abcdef01-abcd-abcd-abcd-abcdef012345"
                             :amount          50.0
                             :currency        "EUR"
                             :code            "vH72Hks209"
                             :state           "NEW"
                             :target-audience "scientists@university.com"

                             :acl             valid-acl-admin
                             }

        valid-voucher-user (assoc valid-voucher-admin :acl valid-acl-user)]

    ;; admin/user query succeeds but is empty
    (doseq [session [session-admin session-user]]
      (-> session
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?)
          (ltu/is-operation-present "add")
          (ltu/is-operation-absent "delete")
          (ltu/is-operation-absent "edit")))

    ;; anon query fails
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; anon create must fail
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-voucher-admin))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; check voucher creation
    (let [admin-uri (-> session-admin
                        (request base-uri
                                 :request-method :post
                                 :body (json/write-str valid-voucher-admin))
                        (ltu/body->edn)
                        (ltu/is-status 201)
                        (ltu/location))

          admin-abs-uri (str p/service-context admin-uri)

          user-uri (-> session-user
                       (request base-uri
                                :request-method :post
                                :body (json/write-str valid-voucher-user))
                       (ltu/body->edn)
                       (ltu/is-status 201)
                       (ltu/location))

          user-abs-uri (str p/service-context user-uri)]

      ;; admin should see 2 voucher resources
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

      ;; verify contents of admin voucher
      (let [voucher-full (-> session-admin
                             (request admin-abs-uri)
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/is-operation-present "edit")
                             (ltu/is-operation-present "delete")
                             (ltu/is-operation-present "activate")
                             (ltu/is-operation-present "expire"))
            voucher (:body (:response voucher-full))
            activate-url (str p/service-context (ltu/get-op voucher-full "activate"))
            expire-url (str p/service-context (ltu/get-op voucher-full "expire"))]

        (is (= "my-voucher" (:name voucher)))
        (is (= "scientists@university.com" (:target-audience voucher)))

        ;; check activation acls - fail as anon
        (-> session-anon
            (request activate-url
                     :request-method :post)
            (ltu/body->edn)
            (ltu/is-status 403))

        ;; check expire acls - fail as anon
        (-> session-anon
            (request expire-url
                     :request-method :post)
            (ltu/body->edn)
            (ltu/is-status 403))

        ;; success as regular user
        (-> session-user
            (request activate-url
                     :request-method :post)
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; now the redeem url should finally exist
        (let [voucher-updated (-> session-admin
                                  (request admin-abs-uri)
                                  (ltu/body->edn)
                                  (ltu/is-status 200)
                                  (ltu/is-operation-present "redeem"))
              redeem-url (str p/service-context (ltu/get-op voucher-updated "redeem"))]

          ;; state is activated but anon cannot redeem, anon doesn't have can-edit
          (-> session-anon
              (request redeem-url
                       :request-method :post)
              (ltu/body->edn)
              (ltu/is-status 403))

          ; but user can redeem
          (-> session-user
              (request redeem-url
                       :request-method :post)
              (ltu/body->edn)
              (ltu/is-status 200)))

        ;; can expire it
        (-> session-user
            (request expire-url
                     :request-method :post)
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; but cannot expire if already expired
        (-> session-user
            (request expire-url
                     :request-method :post)
            (ltu/body->edn)
            (ltu/is-status 400))

        ;; verify that an edit works
        (let [updated (assoc voucher :target-audience "scientists@university.com")]

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

            (is (= "scientists@university.com" (:target-audience updated-body))))))

      ;; admin can delete the voucher
      (-> session-admin
          (request admin-abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; user cannot delete the voucher
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
