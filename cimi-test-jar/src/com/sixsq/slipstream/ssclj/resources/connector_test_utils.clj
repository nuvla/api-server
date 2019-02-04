(ns com.sixsq.slipstream.ssclj.resources.connector-test-utils
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.app.params :as p]

    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.connector :as con]
    [com.sixsq.slipstream.ssclj.resources.connector-template :as ct]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [peridot.core :refer :all])
  (:import (clojure.lang ExceptionInfo)))

(def base-uri (str p/service-context (u/de-camelcase con/resource-name)))
(def tpl-base-uri (str p/service-context (u/de-camelcase ct/resource-name)))

(defn new-instance-name
  [cloud-service-type]
  (str cloud-service-type "-" (System/currentTimeMillis)))


;;
;; Tests.
;;

(defn connector-lifecycle

  ([cloud-service-type]
   (connector-lifecycle cloud-service-type nil))

  ([cloud-service-type provided-template]
   (let [href (str ct/resource-url "/" cloud-service-type)
         template-url (str p/service-context ct/resource-url "/" cloud-service-type)

         session-anon (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
         session-admin (header session-anon authn-info-header "root ADMIN")

         resp (-> session-admin
                  (request template-url)
                  (ltu/body->edn)
                  (ltu/is-status 200))
         template (or provided-template (get-in resp [:response :body]))
         valid-create {:connectorTemplate (-> template
                                              ltu/strip-unwanted-attrs
                                              (assoc :instanceName (new-instance-name cloud-service-type)))}
         href-create {:connectorTemplate (merge provided-template {:href         href
                                                                   :instanceName (new-instance-name cloud-service-type)})}
         invalid-create (assoc-in valid-create [:connectorTemplate :invalid] "BAD")]

     ;; admin create with invalid template fails
     (-> session-admin
         (request base-uri
                  :request-method :post
                  :body (json/write-str invalid-create))
         (ltu/body->edn)
         (ltu/is-status 400))

     ;; full connector lifecycle as administrator should work
     (let [uri (-> session-admin
                   (request base-uri
                            :request-method :post
                            :body (json/write-str valid-create))
                   (ltu/body->edn)
                   (ltu/is-status 201)
                   (ltu/location))
           abs-uri (str p/service-context (u/de-camelcase uri))]


       ;; create again with the same connector instance name should fail with 409
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
                         (ltu/is-resource-uri con/collection-uri)
                         (ltu/is-count 1)
                         (ltu/entries con/resource-tag))]
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
           abs-uri (str p/service-context (u/de-camelcase uri))]

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
           (ltu/is-status 404))))))


(defn connector-template-is-registered
  [cloud-service-type]
  (let [id (str ct/resource-url "/" cloud-service-type)
        doc (crud/retrieve-by-id id)]
    (is (= id (:id doc)))))


(defn template-lifecycle
  [cloud-service-type]

  ;; Get all registered connector templates.
  ;; There should be only one connector of this type.
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "root ADMIN")

        entries (-> session-admin
                    (request tpl-base-uri)
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-resource-uri ct/collection-uri)
                    (ltu/is-count pos?)
                    (ltu/is-operation-absent "add")
                    (ltu/is-operation-absent "delete")
                    (ltu/is-operation-absent "edit")
                    (ltu/is-operation-absent "describe")
                    (ltu/entries ct/resource-tag))
        ids (set (map :id entries))
        types (set (map :cloudServiceType entries))]
    (is (contains? ids (str ct/resource-url "/" cloud-service-type)))
    (is (contains? types cloud-service-type))

    ;; Get connector template and work with it.
    (let [entry (first (filter #(= cloud-service-type (:cloudServiceType %)) entries))
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
      (is (:cloudServiceType desc-body))
      (is (:acl desc-body))

      (is (thrown-with-msg? ExceptionInfo #".*resource does not satisfy defined schema.*" (crud/validate entry-body)))
      (is (crud/validate (assoc entry-body :instanceName (new-instance-name cloud-service-type)))))))
