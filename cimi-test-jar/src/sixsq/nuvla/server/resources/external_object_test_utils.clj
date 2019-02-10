(ns sixsq.nuvla.server.resources.external-object-test-utils
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.utils :as u]   ;;con
    [sixsq.nuvla.server.resources.external-object :as eo] ;;ct
    [sixsq.nuvla.server.resources.external-object-template :as eot]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu])
  (:import (clojure.lang ExceptionInfo)))


(def base-uri (str p/service-context eo/resource-type))
(def tpl-base-uri (str p/service-context eot/resource-type))

(defn new-instance-name
  [objectType]
  (str objectType "-" (System/currentTimeMillis)))

;;
;; Tests.
;;
(defn external-object-lifecycle
  [objectType]
  (let [href (str eot/resource-url "/" objectType)
        template-url (str p/service-context eot/resource-url "/" objectType)

        session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "root ADMIN")

        resp (-> session-admin
                 (request template-url)
                 (ltu/body->edn)
                 (ltu/is-status 200))
        template (get-in resp [:response :body])
        valid-create {:externalObjectTemplate (-> template
                                                  ltu/strip-unwanted-attrs
                                                  (assoc :instanceName (new-instance-name objectType)))}
        href-create {:externalObjectTemplate {:href         href
                                              :instanceName (new-instance-name objectType)}}
        invalid-create (assoc-in valid-create [:externalObjectTemplate :invalid] "BAD")]

    ;; admin create with invalid template fails
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str invalid-create))
        (ltu/body->edn)
        (ltu/is-status 400))


    ;; full external object lifecycle as administrator should work
    (let [uri (-> session-admin
                  (request base-uri
                           :request-method :post
                           :body (json/write-str valid-create))
                  (ltu/body->edn)
                  (ltu/is-status 201)
                  (ltu/location))
          abs-uri (str p/service-context uri)]


      ;; create again with the same external object instance name should fail
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str (assoc valid-create :instanceName uri)))
          (ltu/body->edn)
          (ltu/is-status 400))

      ;; admin get succeeds
      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; anonymous query fails
      (-> session-anon
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; admin query succeeds
      (let [entries (-> session-admin
                        (request base-uri)
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        (ltu/is-resource-uri eo/collection-uri)
                        (ltu/is-count 1)
                        (ltu/entries eo/resource-tag))]
        (is ((set (map :id entries)) uri))

        ;; verify that all entries are accessible
        (let [pair-fn (juxt :id #(str p/service-context (:id %)))
              pairs (map pair-fn entries)]
          (doseq [[id entry-uri] pairs]
            (-> session-admin
                (request entry-uri)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-id id)))))

      ;; admin delete succeeds
      (-> session-admin
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; ensure entry is really gone
      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 404)))

    ;; abbreviated lifecycle using href to template instead of copy
    (let [uri (-> session-admin
                  (request base-uri
                           :request-method :post
                           :body (json/write-str href-create))
                  (ltu/body->edn)
                  (ltu/is-status 201)
                  (ltu/location))
          abs-uri (str p/service-context uri)]

      ;; admin delete succeeds
      (-> session-admin
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; ensure entry is really gone
      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 404)))))

(defn external-object-template-is-registered
  [objectType]
  (let [id (str eot/resource-url "/" objectType)
        doc (crud/retrieve-by-id id)]
    (is (= id (:id doc)))))

(defn template-lifecycle
  [objectType]

  ;; Get all registered external object templates.
  ;; There should be only one external object of this type.
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "root ADMIN")

        entries (-> session-admin
                    (request tpl-base-uri)
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-resource-uri eot/collection-uri)
                    (ltu/is-count pos?)
                    (ltu/is-operation-absent "add")
                    (ltu/is-operation-absent "delete")
                    (ltu/is-operation-absent "edit")
                    (ltu/is-operation-absent "describe")
                    (ltu/entries eot/resource-tag))
        ids (set (map :id entries))
        types (set (map :objectType entries))]
    (is (contains? ids (str eot/resource-url "/" objectType)))
    (is (contains? types objectType))

    ;; Get external object template and work with it.
    (let [entry (first (filter #(= objectType (:objectType %)) entries))
          ops (ltu/operations->map entry)
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

      (is (thrown-with-msg? ExceptionInfo #".*resource does not satisfy defined schema.*" (crud/validate entry-body)))
      (is (crud/validate (assoc entry-body :instanceName (new-instance-name objectType)))))))
