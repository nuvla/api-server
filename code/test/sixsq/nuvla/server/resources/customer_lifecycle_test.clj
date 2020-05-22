;(ns sixsq.nuvla.server.resources.customer-lifecycle-test
;  (:require
;    [clojure.data.json :as json]
;    [clojure.test :refer [deftest is use-fixtures]]
;    [peridot.core :refer [content-type header request session]]
;    [sixsq.nuvla.server.app.params :as p]
;    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
;    [sixsq.nuvla.server.resources.common.utils :as u]
;    [sixsq.nuvla.server.resources.pricing :as pricing]
;    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
;    [sixsq.nuvla.server.resources.customer :as t]
;    [sixsq.nuvla.server.resources.common.user-utils-test :as user-utils-test]
;    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
;    [clojure.tools.logging :as log]))
;
;
;(use-fixtures :once ltu/with-test-server-fixture
;              (partial user-utils-test/with-existing-user "tarzan@example.com"))
;
;
;(def base-uri (str p/service-context t/resource-type))
;
;
;(def valid-acl {:owners ["group/nuvla-admin"]})
;
;
;(def timestamp "1964-08-25T10:00:00.00Z")
;
;
;(def test-identifier "some-user-identifer")
;
;
;(def valid-entry {:plan-id       "plan_HGQ9iUgnz2ho8e"
;                  :plan-item-ids ["plan_HGQIIWmhYmi45G"
;                                  "plan_HIrgmGboUlLqG9"
;                                  "plan_HGQAXewpgs9NeW"
;                                  "plan_HGQqB0p8h86Ija"]})
;
;
;(deftest check-metadata
;  (mdtu/check-metadata-exists t/resource-type))
;
;
;(deftest lifecycle
;
;  (let [session-anon  (-> (session (ltu/ring-app))
;                          (content-type "application/json"))
;        session-admin (header session-anon authn-info-header
;                              "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
;        session-user  (header session-anon authn-info-header (str @user-utils-test/user-id! " group/nuvla-user group/nuvla-anon"))]
;
;    ;; admin create pricing catalogue
;    (-> session-admin
;        (request (str p/service-context pricing/resource-type)
;                 :request-method :post
;                 :body (json/write-str {}))
;        (ltu/body->edn)
;        (ltu/is-status 201))
;
;    ;; create: NOK for anon
;    (-> session-anon
;        (request base-uri
;                 :request-method :post
;                 :body (json/write-str valid-entry))
;        (ltu/body->edn)
;        (ltu/is-status 403))
;
;    ;; create: NOK for admin
;    (-> session-admin
;        (request base-uri
;                 :request-method :post
;                 :body (json/write-str valid-entry))
;        (ltu/body->edn)
;        (ltu/is-status 400)
;        (ltu/message-matches #"Admin can't create customer!"))
;
;    ;; creation should list all required-items
;    (-> session-user
;        (request base-uri
;                 :request-method :post
;                 :body (json/write-str (update valid-entry :plan-item-ids pop)))
;        (ltu/body->edn)
;        (ltu/is-status 400)
;        (ltu/message-matches #"Plan-item-ids not valid for plan.*"))
;
;    ;; creation should list all required-items
;    (-> session-user
;        (request base-uri
;                 :request-method :post
;                 :body (json/write-str (update valid-entry :plan-item-ids conj "plan-item-extra")))
;        (ltu/body->edn)
;        (ltu/is-status 400)
;        (ltu/message-matches #"Plan-item-ids not valid for plan.*"))
;
;    ;; undefined plan
;    (-> session-user
;        (request base-uri
;                 :request-method :post
;                 :body (json/write-str (assoc valid-entry :plan-id "plan-not-exit")))
;        (ltu/body->edn)
;        (ltu/is-status 400)
;        (ltu/message-matches #"Plan-id .* not found!"))
;
;    (let [customer-1 (-> session-user
;                         (request base-uri
;                                  :request-method :post
;                                  :body (json/write-str valid-entry))
;                         (ltu/body->edn)
;                         (ltu/is-status 201)
;                         (ltu/location-url))]
;
;      (let [customer-response (-> session-user
;                                  (request customer-1)
;                                  (ltu/body->edn)
;                                  (ltu/is-status 200)
;                                  (ltu/dump)
;                                  (ltu/is-operation-present :create-subscription)
;                                  (ltu/is-operation-present :create-setup-intent))
;            create-setup-intent (ltu/get-op-url customer-response :create-setup-intent)]
;
;        (-> session-user
;            (request create-setup-intent)
;            (ltu/body->edn)
;            (ltu/is-status 200)
;            (ltu/is-key-value some? :client-secret true)))
;
;      (-> session-user
;          (request base-uri
;                   :request-method :put)
;          (ltu/body->edn)
;          (ltu/is-status 200)
;          (ltu/dump)
;          (ltu/is-count 1))
;      )
;
;    )
;
;  ;;; queries: OK for admin, users, NOK for anon
;  ;(-> session-anon
;  ;    (request base-uri)
;  ;    (ltu/body->edn)
;  ;    (ltu/is-status 403))
;  ;
;  ;(doseq [session [session-jane session-user]]
;  ;  (-> session
;  ;      (request base-uri)
;  ;      (ltu/body->edn)
;  ;      (ltu/is-status 200)
;  ;      (ltu/is-count 0)))
;  ;
;  ;(if (env/env :nuvla-super-password)
;  ;  (-> session-admin
;  ;      (request base-uri)
;  ;      (ltu/body->edn)
;  ;      (ltu/is-status 200)
;  ;      (ltu/is-count 1))
;  ;  (-> session-admin
;  ;      (request base-uri)
;  ;      (ltu/body->edn)
;  ;      (ltu/is-status 200)
;  ;      (ltu/is-count 0)))
;  ;
;  ;
;  ;;; adding, retrieving and  deleting entry as user should succeed
;  ;(let [uri     (-> session-admin
;  ;                  (request base-uri
;  ;                           :request-method :post
;  ;                           :body (json/write-str valid-entry))
;  ;                  (ltu/body->edn)
;  ;                  (ltu/is-status 201)
;  ;                  (ltu/location))
;  ;
;  ;      abs-uri (str p/service-context uri)]
;  ;
;  ;  ;; retrieve: OK for admin, jane; NOK for tarzan, anon
;  ;  (doseq [session [session-user session-anon]]
;  ;    (-> session
;  ;        (request abs-uri)
;  ;        (ltu/body->edn)
;  ;        (ltu/is-status 403)))
;  ;
;  ;  (doseq [session [session-jane session-admin]]
;  ;    (-> session
;  ;        (request abs-uri)
;  ;        (ltu/body->edn)
;  ;        (ltu/is-status 200)))
;  ;
;  ;  ;; check content of the resource
;  ;  (let [expected-id (str t/resource-type "/" (-> valid-entry :identifier u/from-data-uuid))
;  ;        resource    (-> session-admin
;  ;                        (request abs-uri)
;  ;                        (ltu/body->edn)
;  ;                        (ltu/is-status 200)
;  ;                        (ltu/body))]
;  ;
;  ;    (is (= {:id         expected-id
;  ;            :identifier test-identifier
;  ;            :parent     "user/abcdef01-abcd-abcd-abcd-abcdef012345"}
;  ;           (select-keys resource #{:id :identifier :parent}))))
;  ;
;  ;  ;; adding the same resource a second time must fail
;  ;  (-> session-admin
;  ;      (request base-uri
;  ;               :request-method :post
;  ;               :body (json/write-str valid-entry))
;  ;      (ltu/body->edn)
;  ;      (ltu/is-status 409))
;  ;
;  ;  ;; delete: OK for admin; NOK for others
;  ;  (doseq [session [session-anon session-jane session-user]]
;  ;    (-> session
;  ;        (request abs-uri
;  ;                 :request-method :delete)
;  ;        (ltu/body->edn)
;  ;        (ltu/is-status 403)))
;  ;
;  ;  (-> session-admin
;  ;      (request abs-uri
;  ;               :request-method :delete)
;  ;      (ltu/body->edn)
;  ;      (ltu/is-status 200))
;  ;
;  ;  ;; verify that the resource was deleted.
;  ;  (-> session-admin
;  ;      (request abs-uri)
;  ;      (ltu/body->edn)
;  ;      (ltu/is-status 404))))
;
;  )
;
;
;(deftest bad-methods
;  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
;    (ltu/verify-405-status [[base-uri :options]
;                            [base-uri :delete]
;                            [resource-uri :options]
;                            [resource-uri :post]])))
;
;
;
