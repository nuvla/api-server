(ns com.sixsq.slipstream.ssclj.resources.external-object-template-lifecycle-test
  (:require
    [clojure.test :refer [are deftest is use-fixtures]]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.external-object-template :as eot]
    [com.sixsq.slipstream.ssclj.resources.external-object-template-alpha-example :as eotae]
    [com.sixsq.slipstream.ssclj.resources.external-object-template-generic :as eotg]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [peridot.core :refer [content-type header request session]]))


(use-fixtures :each ltu/with-test-server-fixture)

(def collection-uri (str p/service-context (u/de-camelcase eot/resource-name)))

(def eo-tmpl-ids (map #(format "%s/%s" eot/resource-url %) [eotg/objectType
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
                      (ltu/is-operation-absent "describe")
                      (ltu/entries eot/resource-tag))]

      (doseq [entry entries]
        (let [ops (ltu/operations->map entry)
              href (get ops (c/action-uri :describe))
              entry-url (str p/service-context (:id entry))
              describe-url (str p/service-context href)

              entry-resp (-> session-admin
                             (request entry-url)
                             (ltu/is-status 200)
                             (ltu/body->edn))

              entry-body (get-in entry-resp [:response :body])
              desc (-> session-admin
                       (request describe-url)
                       (ltu/body->edn)
                       (ltu/is-status 200))
              desc-body (get-in desc [:response :body])]
          (is (nil? (get ops (c/action-uri :add))))
          (is (nil? (get ops (c/action-uri :edit))))
          (is (nil? (get ops (c/action-uri :delete))))
          (is (:objectType desc-body))
          (is (:acl desc-body))

          (is (crud/validate (dissoc entry-body :id)))

          ;; anonymous access not permitted
          (-> session-anon
              (request entry-url)
              (ltu/is-status 403))
          (-> session-anon
              (request describe-url)
              (ltu/is-status 403))

          ;; user can access
          (-> session-user
              (request entry-url)
              (ltu/is-status 200))
          (-> session-user
              (request describe-url)
              (ltu/is-status 200)))))))
