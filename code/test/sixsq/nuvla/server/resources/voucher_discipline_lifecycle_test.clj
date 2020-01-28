(ns sixsq.nuvla.server.resources.voucher-discipline-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.voucher-discipline :as t]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def valid-acl-admin {:owners    ["group/nuvla-admin"]
                      :view-data ["group/nuvla-user"]})

(def valid-acl-user {:owners    ["group/ocre-user"]
                     :view-data ["group/ocre-user"]})


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest lifecycle
  (let [session-anon        (-> (ltu/ring-app)
                              session
                              (content-type "application/json"))
        session-admin       (header session-anon authn-info-header "user/abcdef01-abcd-abcd-abcd-abcdef012347 group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user        (header session-anon authn-info-header "user/abcdef01-abcd-abcd-abcd-abcdef012346 group/nuvla-user group/nuvla-anon")

        session-ocre        (header session-anon authn-info-header "user/abcdef01-abcd-abcd-abcd-abcdef012348 group/ocre-user")

        valid-voucher-discipline-admin {:name            "my-voucher-discipline"
                                        :description     "my-voucher-discipline description"

                                        :acl             valid-acl-admin
                                        }

        valid-voucher-discipline-user  (assoc valid-voucher-discipline-admin :acl valid-acl-user :name "new discipline")]

    ;; admin/user query succeeds but is empty
    (doseq [session [session-admin session-ocre]]
      (-> session
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present :add)
        (ltu/is-operation-absent :delete)
        (ltu/is-operation-absent :edit)))

    ;; anon query fails
    (-> session-anon
      (request base-uri)
      (ltu/body->edn)
      (ltu/is-status 403))

    ;; normal user query fails
    (-> session-user
      (request base-uri)
      (ltu/body->edn)
      (ltu/is-status 403))

    ;; ocre query succeeds, and has same operation rights as admin
    (-> session-ocre
      (request base-uri)
      (ltu/body->edn)
      (ltu/is-status 200)
      (ltu/is-count zero?)
      (ltu/is-operation-present :add)
      (ltu/is-operation-absent :delete)
      (ltu/is-operation-absent :edit))

    ;; anon create must fail
    (-> session-anon
      (request base-uri
        :request-method :post
        :body (json/write-str valid-voucher-discipline-admin))
      (ltu/body->edn)
      (ltu/is-status 403))

    ;; check voucher-discipline creation
    (let [admin-uri     (-> session-admin
                          (request base-uri
                            :request-method :post
                            :body (json/write-str valid-voucher-discipline-admin))
                          (ltu/body->edn)
                          (ltu/is-status 201)
                          (ltu/location))

          admin-abs-uri (str p/service-context admin-uri)

          ocre-uri      (-> session-ocre
                          (request base-uri
                            :request-method :post
                            :body (json/write-str valid-voucher-discipline-user))
                          (ltu/body->edn)
                          (ltu/is-status 201)
                          (ltu/location))

          ocre-abs-uri  (str p/service-context ocre-uri)]

      ;; admin should see 2 voucher-discipline resources
      (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-resource-uri t/collection-type)
        (ltu/is-count 2))

      ;; ocre user only sees 1 cause of acls
      (-> session-ocre
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-resource-uri t/collection-type)
        (ltu/is-count 1))

      ;; verify contents of admin voucher-discipline
      (let [voucher-discipline-full (-> session-admin
                                      (request admin-abs-uri)
                                      (ltu/body->edn)
                                      (ltu/is-status 200)
                                      (ltu/is-operation-present :edit)
                                      (ltu/is-operation-present :delete))
            voucher-discipline      (:body (:response voucher-discipline-full))]

        (is (= "my-voucher-discipline" (:name voucher-discipline)))

        ;; verify that an edit works
        (let [updated (assoc voucher-discipline :name "edit name")]

          (-> session-admin
            (request admin-abs-uri
              :request-method :put
              :body (json/write-str updated))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/body))

          (let [updated-body (-> session-admin
                               (request admin-abs-uri)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/body))]

            (is (= "edit name" (:name updated-body))))))

      ;; admin can delete the voucher-discipline
      (-> session-admin
        (request admin-abs-uri :request-method :delete)
        (ltu/body->edn)
        (ltu/is-status 200))

      ;; ocre user can delete the voucher-discipline
      (-> session-ocre
        (request ocre-abs-uri :request-method :delete)
        (ltu/body->edn)
        (ltu/is-status 200)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [resource-uri :options]
                            [resource-uri :post]])))
