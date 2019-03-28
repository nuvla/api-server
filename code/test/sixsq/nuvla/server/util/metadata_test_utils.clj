(ns sixsq.nuvla.server.util.metadata-test-utils
  (:require
    [clojure.test :refer [is]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.resource-metadata :as md]))


(def base-uri (str p/service-context md/resource-type))


(defn get-generated-metadata
  [typeURI]

  (let [session (-> (ltu/ring-app)
                    session
                    (header authn-info-header "group/nuvla-anon")
                    (content-type "application/json"))

        md-docs (-> session
                    (request base-uri
                             :method :put)
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-count pos?)
                    (ltu/entries))]

    (first (filter #(-> % :typeURI (= typeURI)) md-docs))))


(defn check-metadata-exists
  [typeURI]

  (let [session (-> (ltu/ring-app)
                    session
                    (header authn-info-header "group/nuvla-anon")
                    (content-type "application/json"))

        md-docs (-> session
                    (request base-uri
                             :method :put)
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-count pos?)
                    (ltu/entries))

        typeURIs (set (map :typeURI md-docs))
        ids (set (map u/document-id (map :id md-docs)))]

    (is (set? typeURIs))
    (is (set? ids))

    (when (and typeURIs ids)
      (is (typeURIs typeURI))
      (is (ids typeURI)))))
