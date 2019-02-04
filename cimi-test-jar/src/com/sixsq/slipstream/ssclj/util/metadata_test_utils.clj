(ns com.sixsq.slipstream.ssclj.util.metadata-test-utils
  (:require
    [clojure.test :refer [is]]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.slipstream.ssclj.resources.resource-metadata :as md]
    [peridot.core :refer [content-type header request session]]))


(def base-uri (str p/service-context (u/de-camelcase md/resource-name)))


(defn get-generated-metadata
  [typeURI]

  (let [session (-> (ltu/ring-app)
                    session
                    (header authn-info-header "ANON")
                    (content-type "application/json"))

        md-docs (-> session
                    (request base-uri
                             :method :put)
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-count pos?)
                    :response
                    :body
                    :resourceMetadatas)]

    (first (filter #(-> % :typeURI (= typeURI)) md-docs))))


(defn check-metadata-exists
  [typeURI]

  (let [session (-> (ltu/ring-app)
                    session
                    (header authn-info-header "ANON")
                    (content-type "application/json"))

        md-docs (-> session
                    (request base-uri
                             :method :put)
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-count pos?)
                    :response
                    :body
                    :resourceMetadatas)

        typeURIs (set (map :typeURI md-docs))
        ids (set (map u/document-id (map :id md-docs)))]

    (is (set? typeURIs))
    (is (set? ids))

    (when (and typeURIs ids)
      (is (typeURIs typeURI))
      (is (ids typeURI)))))
