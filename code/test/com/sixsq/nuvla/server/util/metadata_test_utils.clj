(ns com.sixsq.nuvla.server.util.metadata-test-utils
  (:require
    [clojure.test :refer [is]]
    [peridot.core :refer [content-type header request session]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]))


(def base-uri (str p/service-context md/resource-type))


(defn get-generated-metadata
  [type-uri]

  (let [session (-> (ltu/ring-app)
                    session
                    (header authn-info-header "group/nuvla-anon")
                    (content-type "application/json"))

        ;; done as an unfiltered search because filtering doesn't work with in-memory resources
        md-docs (-> session
                    (request base-uri)
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-count pos?)
                    (ltu/entries))]

    (first (filter #(-> % :type-uri (= type-uri)) md-docs))))


(defn check-metadata-exists
  [& test-type-uris]

  (let [session   (-> (ltu/ring-app)
                      session
                      (header authn-info-header "group/nuvla-anon")
                      (content-type "application/json"))

        ;; done as an unfiltered search because filtering doesn't work with in-memory resources
        md-docs   (-> session
                      (request base-uri)
                      (ltu/body->edn)
                      (ltu/is-status 200)
                      (ltu/is-count pos?)
                      (ltu/entries))

        type-uris (set (map :type-uri md-docs))
        ids       (set (map u/id->uuid (map :id md-docs)))]

    (is (set? type-uris))
    (is (set? ids))

    (when (and type-uris ids)
      (doseq [type-uri test-type-uris]
        (is (type-uris type-uri) (str type-uri " not found in type-uris"))
        (is (ids type-uri) (str type-uri " not found in ids"))))))
