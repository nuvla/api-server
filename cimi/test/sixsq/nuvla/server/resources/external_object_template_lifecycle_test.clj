(ns sixsq.nuvla.server.resources.external-object-template-lifecycle-test
  (:require
    [clojure.test :refer [are deftest is use-fixtures]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.external-object-template :as eot]
    [sixsq.nuvla.server.resources.external-object-template-alpha-example :as eotae]
    [sixsq.nuvla.server.resources.external-object-template-generic :as eotg]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [peridot.core :refer [content-type header request session]]))


(use-fixtures :each ltu/with-test-server-fixture)

(def collection-uri (str p/service-context eot/resource-type))

(def eo-tmpl-ids (map #(format "%s/%s" eot/resource-type %) [eotg/objectType
                                                            eotae/objectType]))

(deftest check-retrieve-by-id
  (doseq [eo-tmpl-id eo-tmpl-ids]
    (let [doc (crud/retrieve-by-id eo-tmpl-id)]
      (is (= eo-tmpl-id (:id doc))))))

(deftest lifecycle
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-user (header session-anon authn-info-header "jane USER")
        session-admin (header session-anon authn-info-header "root ADMIN")]


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


    ;; query as ADMIN should work correctly
    (let [entries (-> session-admin
                      (content-type "application/x-www-form-urlencoded")
                      (request (str collection-uri))
                      (ltu/body->edn)
                      (ltu/is-status 200)
                      (ltu/is-resource-uri eot/collection-uri)
                      (ltu/is-count pos?)
                      (ltu/is-operation-absent "add")
                      (ltu/is-operation-absent "delete")
                      (ltu/is-operation-absent "edit")
                      (ltu/entries))]

      (doseq [entry entries]
        (let [ops (ltu/operations->map entry)
              entry-url (str p/service-context (:id entry))

              entry-resp (-> session-admin
                             (request entry-url)
                             (ltu/is-status 200)
                             (ltu/body->edn))

              entry-body (get-in entry-resp [:response :body])]
          (is (nil? (get ops (c/action-uri :add))))
          (is (nil? (get ops (c/action-uri :edit))))
          (is (nil? (get ops (c/action-uri :delete))))

          (is (crud/validate (dissoc entry-body :id)))

          ;; anonymous access not permitted
          (-> session-anon
              (request entry-url)
              (ltu/is-status 403))

          ;; user can access
          (-> session-user
              (request entry-url)
              (ltu/is-status 200)))))))
