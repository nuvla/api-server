(ns sixsq.nuvla.server.resources.api-docs.openapi
  (:require [clojure.string :as str]
            [sixsq.nuvla.server.resources.common.crud :as crud]
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


(defn- schema-ref
  "Returns the OpenAPI structure representing a reference to a schema."
  [schema-name]
  {:$ref (str "#/components/schemas/" schema-name)})


(defn resource-get
  "Returns the OpenAPI method spec for the retrieve operation"
  [{resource-type-name :name :as resource-metadata}]
  {:get
   {:tags        [resource-type-name]
    :description (str "Returns a " resource-type-name)
    :responses   {200 {:description (str "A " resource-type-name)
                       :content
                       {"application/json"
                        {:schema (schema-ref (openapi-schema/resource-schema-name resource-metadata))}
                        ;; TODO: generate examples ?
                        ;; :examples {}
                        }}
                  403 {:description "Not authorized"}
                  404 {:description "The resource was not found"}}}})


(defn resource-create
  "Returns the OpenAPI method spec for the create operation"
  [{resource-type-name :name :as resource-metadata}]
  {:post
   {:tags        [resource-type-name]
    :description (str "Creates a " resource-type-name)
    :requestBody {:content
                  {"application/json"
                   {:schema (schema-ref (openapi-schema/resource-create-schema-name resource-metadata))}}
                  :required true}
    :responses   {201 {:description (str "The " resource-type-name " was created successfully")
                       :content
                       {"application/json"
                        {:schema (schema-ref (openapi-schema/resource-schema-name resource-metadata))
                         ;; {:status 201
                         ;;  :message "module/0eb4ab07-4552-4f73-87f7-9c28fbdba21a created"
                         ;;  :resource-id "module/0eb4ab07-4552-4f73-87f7-9c28fbdba21a"}},
                         }
                        ;; TODO: generate examples ?
                        ;; :examples {}
                        }}
                  403 {:description "Not authorized"}}}})


(defn resource-edit
  "Returns the OpenAPI method spec for the edit operation"
  [{resource-type-name :name :as resource-metadata}]
  {:put
   {:tags        [resource-type-name]
    :description (str "Updates a " resource-type-name)
    :requestBody {:content
                  {"application/json"
                   {:schema (schema-ref (openapi-schema/resource-update-schema-name resource-metadata))}}
                  :required true}
    :responses   {200 {:description (str "The " resource-type-name " was updated successfully")
                       :content
                       {"application/json"
                        {:schema (schema-ref (openapi-schema/resource-schema-name resource-metadata))}
                        ;; {:path "example"
                        ;;  :updated "2023-07-11T08:54:36.560Z"
                        ;;  :created "1964-08-25T10:00:00.00Z"
                        ;;  :parent-path ""
                        ;;  :updated-by "group/nuvla-admin"
                        ;;  :created-by "group/nuvla-admin"
                        ;;  :id "module/4d9c6022-0101-4c8d-995e-67dce1a44125"
                        ;;  :resource-type "module"
                        ;;  :acl {:owners ["group/nuvla-admin"]
                        ;;        :view-meta ["user/jane"]
                        ;;        :view-data ["user/jane"]
                        ;;        :view-acl ["user/jane"]}
                        ;;        :subtype "project"}}
                        }}
                  403 {:description "Not authorized"}
                  404 {:description "The resource was not found"}}}})


(defn resource-delete
  "Returns the OpenAPI method spec for the delete operation"
  [{resource-type-name :name :as resource-metadata}]
  {:delete
   {:tags        [resource-type-name]
    :description (str "Deletes a " resource-type-name)
    :responses   {200 {:description (str "The " resource-type-name " was deleted successfully")}
                  ;; :body {:status 200
                  ;;        :message "module-application/eb6bc0fa-c200-438a-ae9b-290653619f1d deleted"
                  ;;        :resource-id "module-application/eb6bc0fa-c200-438a-ae9b-290653619f1d"}}
                  403 {:description "Not authorized"}
                  ;; :body {:status 403
                  ;;        :message "invalid credentials for 'module-application/f8d83931-598a-464d-bc52-13961db5755e'"
                  ;;        :resource-id "module-application/f8d83931-598a-464d-bc52-13961db5755e"}}
                  404 {:description "The resource was not found"}
                  ;; :body {:status 404
                  ;;        :message "module-application/99be4486-40c4-4da5-904a-58e08ee7b65a not found"
                  ;;        :resource-id "module-application/99be4486-40c4-4da5-904a-58e08ee7b65a"}}
                  }}})


(defn resources-query
  "Returns the OpenAPI method spec for the query operation"
  [{resource-type-name :name :as resource-metadata}]
  {:get
   {:tags        [resource-type-name]
    :description (str "Returns a collection of " resource-type-name)
    :responses   {200 {:description (str "A collection of " resource-type-name)
                       :content
                       {"application/json"
                        {:schema (schema-ref (openapi-schema/resource-collection-schema-name resource-metadata))}
                        ;; TODO: generate examples ?
                        ;; :examples {}
                        ;; {:count 0
                        ;;  :acl {:query ["group/nuvla-admin"]
                        ;;        :add ["group/nuvla-admin"]}
                        ;;  :resource-type "module-application-collection"
                        ;;  :id "module-application"
                        ;;  :resources []
                        ;;  :operations
                        ;;  [{:rel "add", :href "module-application"}
                        ;;   {:rel "bulk-delete", :href "module-application"}]}}
                        }}
                  204 {:description (str "The " resource-type-name "s were deleted successfully")}
                  403 {:description "Not authorized"}}}})


(defn resources-bulk-delete
  "Returns the OpenAPI method spec for the bulk delete operation"
  [{resource-type-name :name :as _resource-metadata}]
  {:delete
   {:tags        [resource-type-name]
    :description (str "Deletes a set of " resource-type-name "s in bulk")
    :responses   {204 {:description (str "The " resource-type-name "s were deleted successfully")}
                  403 {:description "Not authorized"}
                  404 {:description "The resources were not found"}}}})


(def ^:const resource-param "resource-id")


(defn resource-param-def
  ""
  [{resource-type-name :name :as _resource-metadata}]
  {:name        resource-param
   :in          "path"
   :required    true
   :description (str "The ID of the " resource-type-name)
   :schema      {:type "string" :format "uuid"}})


(defn collection-level-paths
  "Returns the paths OpenAPI spec for collection level operations."
  [{:keys [type-uri scrud-operations] :as resource-metadata}]
  (let [scrud-op-supported? (set scrud-operations)
        collection-ops      (merge
                              (when (scrud-op-supported? :add)
                                (resource-create resource-metadata))
                              (when (scrud-op-supported? :query)
                                (resources-query resource-metadata))
                              (when (scrud-op-supported? :bulk-delete)
                                (resources-bulk-delete resource-metadata)))]
    (when (seq collection-ops)
      {(str "/" type-uri) collection-ops})))


(defn resource-level-paths
  "Returns the paths OpenAPI spec for resource level operations."
  [{:keys [type-uri scrud-operations] :as resource-metadata}]
  (let [scrud-op-supported? (set scrud-operations)
        resource-ops        (merge
                              (when (scrud-op-supported? :get)
                                (resource-get resource-metadata))
                              (when (scrud-op-supported? :edit)
                                (resource-edit resource-metadata))
                              (when (scrud-op-supported? :delete)
                                (resource-delete resource-metadata)))]
    (when (seq resource-ops)
      {(str "/" type-uri "/{" resource-param "}")
       (merge
         {:parameters [(resource-param-def resource-metadata)]}
         resource-ops)})))


(defn action-paths
  "Returns the paths OpenAPI spec for actions."
  [{:keys [type-uri actions] resource-type-name :name :as resource-metadata}]
  (->> actions
       (reduce
         (fn [m {:keys       [uri method description input-message output-message input-parameters]
                 action-name :name}]
           (assoc
             m
             (str "/" type-uri "/{" resource-param "}/" uri)
             {:parameters [(resource-param-def resource-metadata)]
              (-> method str/lower-case keyword)
              (cond->
                {:tags        [resource-type-name]
                 :description description
                 :responses   (merge (when output-message
                                       {200 {:description (str "The " resource-type-name " was updated successfully")
                                             :content     {output-message
                                                           {:schema {:type "object"}}}}})
                                     {403 {:description "Not authorized"}})}
                (and input-message input-parameters)
                (assoc :requestBody {:content
                                     {input-message
                                      {:schema {:type       "object"
                                                :properties (zipmap
                                                              (map :name input-parameters)
                                                              (map #(openapi-schema/transform-type->openapi-type
                                                                      (select-keys % [:type]))
                                                                   input-parameters))}}}
                                     :required true}))}))
         {})))


(defn resource-paths
  "Returns the `paths` section of the OpenAPI spec for the specified resource."
  [resource-metadata]
  (merge
    (resource-level-paths resource-metadata)
    (collection-level-paths resource-metadata)
    (action-paths resource-metadata)))


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
