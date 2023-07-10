(ns sixsq.nuvla.server.resources.api-docs.openapi
  (:require [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.server.resources.spec.email :as email]
            [sixsq.nuvla.server.resources.resource-metadata :as resource-metadata]
            [sixsq.nuvla.server.resources.api-docs.openapi-schema :as openapi-schema]
            [spec-tools.openapi.core :as openapi]))


(defn tags
  "Returns the `tags` section of the OpenAPI spec."
  [resources-metadata]
  (->> resources-metadata
       (map #(select-keys % [:name :description]))
       (sort-by :name)))


(defn resource-get
  "Returns the OpenAPI method spec for the retrieve operation"
  [{resource-type-name :name :as resource-metadata}]
  {:get
   {:tags        [resource-type-name]
    :description (str "Returns a " resource-type-name)
    :responses   {200 {:description (str "A " resource-type-name)
                       :content
                       {"application/json"
                        {:schema {:$ref (str "#/components/schemas/"
                                             (openapi-schema/resource-schema-name resource-metadata))}}
                        ;; TODO: generate examples ?
                        ;; :examples {}
                        }}
                  403 {:description "Not authorized"}
                  404 {:description "The resource was not found"}}}})


(defn collection-query
  "Returns the OpenAPI method spec for the query operation"
  [{resource-type-name :name :as _resource-metadata}]
  {:get
   {:tags        [resource-type-name]
    :description (str "Returns a collection of " resource-type-name)
    :responses   {}}})


(defn resource-paths
  "Returns the `paths` section of the OpenAPI spec for the specified resource."
  [{:keys [type-uri scrud-operations] resource-type-name :name :as resource-metadata}]
  (let [scrud-op-supported?  (set scrud-operations)
        resource-param "resource-id"
        resource-ops   (merge
                         (when (scrud-op-supported? :get)
                           (resource-get resource-metadata)))
        collection-ops (merge
                         (when (scrud-op-supported? :query)
                           (collection-query resource-metadata)))]
    (merge
      (when (seq collection-ops)
        {(str "/" type-uri) collection-ops})
      (when (seq resource-ops)
        {(str "/" type-uri "/{" resource-param "}")
         (merge
           {:parameters [{:name        resource-param
                          :in          "path"
                          :required    true
                          :description (str "The ID of the " resource-type-name)
                          :schema      {:type   "string"
                                        :format "uuid"}}]}
           resource-ops)}))))


(defn paths
  "Returns the `paths` section of the OpenAPI spec."
  [resources-metadata]
  (reduce (fn [m resource-metadata]
            (merge m (resource-paths resource-metadata)))
          (sorted-map)
          resources-metadata))


(defn schemas
  "Returns the `schemas` section of the OpenAPI spec."
  [resources-metadata]
  (->> resources-metadata
       (map openapi-schema/->openapi-schemas)
       (apply merge)
       (into (sorted-map))))


(defn openapi-spec
  "Returns the OpenAPI spec for the given resource types."
  [resources-metadata]
  (let [nuvla-api-spec {:openapi      "3.0.3"
                        :info         {:title          "Nuvla API"
                                       :description    "HTTP-based RESTful API, for the management of Nuvla resources."
                                       :termsOfService "https://docs.nuvla.io/legal/legal/"
                                       :contact        {:name  "API Support",
                                                        :email "apiteam@nuvla.io"}
                                       :license        {:name "Apache 2.0",
                                                        :url  "https://www.apache.org/licenses/LICENSE-2.0.html"}
                                       ;; TODO: add the api version
                                       :version        "<version>"}
                        :externalDocs {:description "Find out more about Nuvla"
                                       :url         "https://docs.nuvla.io/nuvla/user-guide"}
                        :servers      [{:url         "https://nuvla.io/api"
                                        :description "Main (production) server"}]
                        :tags         (tags resources-metadata)
                        :paths        (paths resources-metadata)
                        :components   {:schemas (schemas resources-metadata)}}]
    #_(with-redefs [spec-tools.impl/extract-form
                    (fn [spec]
                      (if (seq? spec) spec (openapi-schema/->openapi-schema spec)))]
        (openapi/openapi-spec nuvla-api-spec))
    nuvla-api-spec))

(defn nuvla-openapi-spec
  "Returns the OpenAPI spec for the Nuvla API."
  []
  (let [resources-metadata (vals @resource-metadata/templates)
        #_[(get @resource-metadata/templates "resource-metadata/email")]
        ;; TODO: figure out why crud/query-as-admin returns 0 results
        ;; to avoid accessing the templates atom directly as done above
        #_(crud/query-as-admin "resource-metadata" {:cimi-params {}})]
    (openapi-spec resources-metadata)))
