(ns sixsq.nuvla.server.resources.deployment-fleet-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer [deftest is use-fixtures testing]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.deployment-fleet :as t]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.module :as module]
    [sixsq.nuvla.server.resources.module-application :as module-application]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def module-base-uri (str p/service-context module/resource-type))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["user/jane"]})


(def timestamp "1964-08-25T10:00:00.00Z")

(def session-id "session/324c6138-aaaa-bbbb-cccc-af3ad15815db")


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest lifecycle
  (let [session-anon           (-> (ltu/ring-app)
                                   session
                                   (content-type "application/json"))
        session-admin          (header session-anon authn-info-header
                                       (str "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon " session-id))
        session-user           (header session-anon authn-info-header
                                       (str "user/jane user/jane group/nuvla-user group/nuvla-anon " session-id))

        valid-deployment-fleet {:spec {:targets      ["credential/a2dc1733-ac2c-45b1-b68a-0ec02653bc0c"]
                                       :applications ["module/a2dc1733-ac2c-45b1-b68a-0ec02653bc0c"]}}]

    (testing "admin/user query succeeds but is empty"
      (doseq [session [session-admin session-user]]
        (-> session
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count zero?)
            (ltu/is-operation-present :add)
            (ltu/is-operation-absent :delete)
            (ltu/is-operation-absent :edit))))

    (testing "anon query fails"
      (-> session-anon
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 403)))

    (testing "anon create must fail"
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str {}))
          (ltu/body->edn)
          (ltu/is-status 403)))

    ;; check deployment creation
    (testing "create must be possible for user"
      (let [{{{:keys [resource-id
                      location]} :body}
             :response} (-> session-user
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str valid-deployment-fleet))
                            (ltu/body->edn)
                            (ltu/is-status 202))

            deployment-fleet-url (str p/service-context resource-id)]

        (testing "user query should see one document"
          (-> session-user
             (request base-uri)
             (ltu/body->edn)
             (ltu/is-status 200)
             (ltu/is-resource-uri t/collection-type)
             (ltu/is-count 1)))

        (testing "user retrieve should work and contain job"
          (-> session-user
              (request deployment-fleet-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :state "CREATING")
              (ltu/is-key-value map? :spec true)
              (ltu/is-key-value :job location)))

        (testing "job is created"
          (-> session-user
              (request (str p/service-context location))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :action "create")
              (ltu/is-key-value :href :target-resource resource-id)))))
    ))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))
