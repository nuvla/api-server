(ns sixsq.nuvla.server.resources.data-object-test-utils
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [is]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.data-object :as data-obj]
    [sixsq.nuvla.server.resources.data-object-template :as data-obj-tpl]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu])
  (:import
    (clojure.lang ExceptionInfo)))


(def base-uri (str p/service-context data-obj/resource-type))


(def tpl-base-uri (str p/service-context data-obj-tpl/resource-type))


(defn new-instance-name
  [objectType]
  (str objectType "-" (System/currentTimeMillis)))


;;
;; Tests.
;;

(defn data-object-lifecycle
  [objectType]

  (let [href           (str data-obj-tpl/resource-type "/" objectType)
        template-url   (str p/service-context data-obj-tpl/resource-type "/" objectType)

        session-anon   (-> (ltu/ring-app)
                           session
                           (content-type "application/json"))
        session-admin  (header session-anon authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")

        template       (-> session-admin
                           (request template-url)
                           (ltu/body->edn)
                           (ltu/is-status 200)
                           (ltu/body))

        valid-create   {:template (-> template
                                      ltu/strip-unwanted-attrs
                                      (assoc :instanceName (new-instance-name objectType)))}
        href-create    {:template {:href         href
                                   :instanceName (new-instance-name objectType)}}
        invalid-create (assoc-in valid-create [:template :invalid] "BAD")]

    ;; admin create with invalid template fails
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str invalid-create))
        (ltu/body->edn)
        (ltu/is-status 400))


    ;; full data object lifecycle as administrator should work
    (let [uri     (-> session-admin
                      (request base-uri
                               :request-method :post
                               :body (json/write-str valid-create))
                      (ltu/body->edn)
                      (ltu/is-status 201)
                      (ltu/location))
          abs-uri (str p/service-context uri)]


      ;; create again with the same data object instance name should fail
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
                        (ltu/is-resource-uri data-obj/collection-type)
                        (ltu/is-count 1)
                        (ltu/entries))]
        (is ((set (map :id entries)) uri))

        ;; verify that all entries are accessible
        (let [pair-fn (juxt :id #(str p/service-context (:id %)))
              pairs   (map pair-fn entries)]
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
    (let [uri     (-> session-admin
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

(defn data-object-template-is-registered
  [objectType]
  (let [id  (str data-obj-tpl/resource-type "/" objectType)
        doc (crud/retrieve-by-id id)]
    (is (= id (:id doc)))))

(defn template-lifecycle
  [objectType]

  ;; Get all registered data object templates.
  ;; There should be only one data object of this type.
  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")

        entries       (-> session-admin
                          (request tpl-base-uri)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/is-resource-uri data-obj-tpl/collection-type)
                          (ltu/is-count pos?)
                          (ltu/is-operation-absent :add)
                          (ltu/is-operation-absent :delete)
                          (ltu/is-operation-absent :edit)
                          (ltu/entries))
        ids           (set (map :id entries))
        types         (set (map :objectType entries))]
    (is (contains? ids (str data-obj-tpl/resource-type "/" objectType)))
    (is (contains? types objectType))

    ;; Get data object template and work with it.
    (let [entry        (first (filter #(= objectType (:objectType %)) entries))
          ops          (ltu/operations->map entry)
          href         (get ops (name :describe))
          entry-url    (str p/service-context (:id entry))
          describe-url (str p/service-context href)

          entry-resp   (-> session-admin
                           (request entry-url)
                           (ltu/is-status 200)
                           (ltu/body->edn))

          entry-body   (get-in entry-resp [:response :body])

          desc         (-> session-admin
                           (request describe-url)
                           (ltu/body->edn)
                           (ltu/is-status 200))
          desc-body    (get-in desc [:response :body])]
      (is (nil? (get ops (name :add))))
      (is (nil? (get ops (name :edit))))
      (is (nil? (get ops (name :delete))))
      (is (:objectType desc-body))
      (is (:acl desc-body))

      (is (thrown-with-msg? ExceptionInfo #".*resource does not satisfy defined schema.*" (crud/validate entry-body)))
      (is (crud/validate (assoc entry-body :instanceName (new-instance-name objectType)))))))
