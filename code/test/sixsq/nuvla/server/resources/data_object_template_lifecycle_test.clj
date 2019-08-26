(ns sixsq.nuvla.server.resources.data-object-template-lifecycle-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.data-object-template :as dot]
    [sixsq.nuvla.server.resources.data-object-template-alpha-example :as dotae]
    [sixsq.nuvla.server.resources.data-object-template-generic :as dotg]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


(use-fixtures :once ltu/with-test-server-fixture)

(def collection-uri (str p/service-context dot/resource-type))

(def do-tmpl-ids (map #(format "%s/%s" dot/resource-type %) [dotg/data-object-type
                                                             dotae/data-object-subtype]))

(deftest check-retrieve-by-id
  (doseq [eo-tmpl-id do-tmpl-ids]
    (let [doc (crud/retrieve-by-id eo-tmpl-id)]
      (is (= eo-tmpl-id (:id doc))))))

(deftest lifecycle
  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-user  (header session-anon authn-info-header "user/jane group/nuvla-user")
        session-admin (header session-anon authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")]


    ;; anonymous query is not authorized
    (-> session-anon
        (request collection-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; user query is authorized
    (-> session-user
        (request collection-uri)
        (ltu/body->edn)
        (ltu/is-status 200))

    ;; query as group/nuvla-admin should work correctly
    (let [entries (-> session-admin
                      (content-type "application/x-www-form-urlencoded")
                      (request (str collection-uri))
                      (ltu/body->edn)
                      (ltu/is-status 200)
                      (ltu/is-resource-uri dot/collection-type)
                      (ltu/is-count pos?)
                      (ltu/is-operation-absent :add)
                      (ltu/is-operation-absent :delete)
                      (ltu/is-operation-absent :edit)
                      (ltu/entries))]

      (doseq [entry entries]
        (let [ops        (ltu/operations->map entry)
              entry-url  (str p/service-context (:id entry))]
          (is (nil? (get ops (name :add))))
          (is (nil? (get ops (name :edit))))
          (is (nil? (get ops (name :delete))))

          ;; anonymous access not permitted
          (-> session-anon
              (request entry-url)
              (ltu/is-status 403))

          ;; user can access
          (-> session-user
              (request entry-url)
              (ltu/is-status 200)))))))
