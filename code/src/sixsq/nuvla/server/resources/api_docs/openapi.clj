(ns sixsq.nuvla.server.resources.api-docs.openapi
  (:require [clojure.data.json :as json]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.server.resources.resource-metadata :as resource-metadata]
            [sixsq.nuvla.server.resources.spec.common :as common]
            [sixsq.nuvla.server.resources.api-docs.openapi-schema :as openapi-schema]
            [spec-tools.core :as st]
            [spec-tools.openapi.core :as openapi]))


(defn tags
  "Returns the `tags` section of the OpenAPI spec."
  [resources-metadata]
  (->> resources-metadata
       (map (fn [{:keys [description] resource-type-name :name}]
              {:name        resource-type-name
               :description description #_(-> description
                                              (str/replace "\n" "\\n")
                                              (str/replace "\"" "\\\""))}))
       (sort-by :name)))


(defn- schema-ref
  "Returns the OpenAPI structure representing a reference to a schema."
  [schema-name]
  {:$ref (str "#/components/schemas/" schema-name)})


(defn- parameter-ref
  "Returns the OpenAPI structure representing a reference to a parameter."
  [parameter-name]
  {:$ref (str "#/components/parameters/" parameter-name)})


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


(defn path-parameters
  "Returns the global path parameters of the OpenAPI spec."
  []
  {"resourceIdParam"
   {:name        "resourceId"
    :in          "path"
    :required    true
    :description "The ID of the resource"
    :schema      {:type "string" :format "uuid"}}})

(def resource-id-parameter-ref
  (parameter-ref "resourceIdParam"))

(defn query-parameters
  "Returns the global query parameters of the OpenAPI spec."
  []
  {"filterParam"
   {:name        "filter"
    :in          "query"
    :required    false
    :description "Used to return only the set of resources that have an attribute matching a certain value"
    :schema      {:type "string"}
    :examples    {"nameFilter"      {:value "name=\"my-resource\""}
                  "genderAgeFilter" {:value "people/gender!=\"male\" and people/age>=21"}
                  "regexFilter"     {:value "application-name^=\"my-app-\""}}}
   "orderbyParam"
   {:name        "orderby"
    :in          "query"
    :required    false
    :description "To order the returned resources by the specified attribute"
    :schema      {:type "string"}
    :example     "created:desc"}
   "aggregationParam"
   {:name        "aggregation"
    :in          "query"
    :required    false
    :description "On top of the requested resources, it will also return on-the-fly aggregations based on the specified function. Available functions: avg, max, min, sum, cardinality, terms, stats, extendedstats, percentiles, value_count, missing"
    :schema      {:type "string"}
    :example     "avg:people/age"}
   "firstParam"
   {:name        "first"
    :in          "query"
    :required    false
    :description "Returns a range of resources by setting the first and last (1-based) query parameters"
    :schema      {:type "integer"}
    :example     10}
   "lastParam"
   {:name        "last"
    :in          "query"
    :required    false
    :description "Returns a range of resources by setting the first and last (1-based) query parameters"
    :schema      {:type "integer"}
    :example     10}
   "selectParam"
   {:name        "select"
    :in          "query"
    :required    false
    :description "Selects only certain attributes to be returned by the server. Avoiding sending information that will not be useful reduces the load on the network and the server"
    :schema      {:type "string"}
    :example     "people/id"}
   "bulkDeleteFilterParam"
   {:name        "filter"
    :in          "query"
    :required    false
    :description "Used to delete only the set of resources that have an attribute matching a certain value"
    :schema      {:type "string"}
    :examples    {"nameFilter"      {:value "name=\"my-resource\""}
                  "genderAgeFilter" {:value "people/gender!=\"male\" and people/age>=21"}
                  "regexFilter"     {:value "application-name^=\"my-app-\""}}}})


(def query-parameters-refs
  [(parameter-ref "filterParam")
   (parameter-ref "orderbyParam")
   (parameter-ref "aggregationParam")
   (parameter-ref "firstParam")
   (parameter-ref "lastParam")
   (parameter-ref "selectParam")])


(defn security-schemes
  "Returns the security schemes for the OpenAPI spec."
  []
  {:authnHeader {:type "apiKey"
                 :in   "header"
                 :name "nuvla-authn-info"}
   :cookieAuth  {:type "apiKey"
                 :in   "cookie"
                 :name "com.sixsq.nuvla.cookie"}})

(def ^:const nuvla-api-description
  "HTTP-based RESTful API, for the management of Nuvla resources.

  ## Introduction

  Nuvla provides a uniform and extensible HTTP-based RESTful API, for the management of Nuvla resources. A Nuvla resource can be anything you can perform an action on, through Nuvla, like your own user profile, a Nuvla application, credentials, etc.

  Users have at their disposal all the usual CRUD (Create, Read, Update, Delete) operations, plus Searching and Querying.

  | Operation | HTTP method | Object |
  |-----|-----|-------|
  | Search | GET or PUT | resource collection |
  | Add (create) | POST | resource collection |
  | Read | GET | resource |
  | Edit (update) | PUT | resource |
  | Delete | DELETE | resource |

  Finally, due to its versatility, Nuvla’s API also provide custom operations for certain resources. These will be covered individually in the subsections below.\n\nHere are a few examples on how to construct the different HTTP requests:

  GET all the resources of a specific type: `GET /api/<resource-name>`

  GET a specific resource: `GET /api/<resource-name>/<resource-uuid>`

  CREATE a new resource:
  ```POST /api/<resource-name>
     HEADERS Content-type:application/json
     DATA <JSON resource>
  ```

  EDIT an existing resource:
  ```PUT /api/<resource-name>/<resource-uuid>
     HEADERS Content-type:application/json
     DATA <JSON with the attribute name and value to be changed>
  ```

  DELETE a resource: `DELETE /api/<resource-name>/<resource-uuid>`

  ## Understanding the Nuvla REST API output format
  All the Nuvla API calls will return a JSON output, and you’ll notice that all of these outputs contain a set of common attributes:

  - id: unique resource identifier, defined by the API server
  - acl: fine-grained access-control list, used for managing authorization process for each resource and its collections of resources. If not defined by the user, the API server will default it based on the requesting user credentials.
  - created: timestamp of creation, defined by the API server
  - updated: timestamp of the last update, defined by the API server
  - resource-type: type of resource, defined by the API server
  - operations: set of available operations for that resource, defined by the API server
  - name: optional user-friendly name for a specific resource, defined by the user or defaulted to None if undefined
  - description: optional verbose description for a specific resource, defined by the user or defaulted to None if undefined
  - API Syntax
  - The Nuvla REST API endpoints are constructed with the following pattern:

  `/api/<resource-name>/<resource-uuid>/<action>`

  where

  <resource-name> is the Kebab Case name of the resource collection you’re accessing,

  <resource-uuid> is the unique identifier for the specific resource you’re managing,

  <action> is a custom additional operation that might be allowed for that resource.

  On top of that, Nuvla’s REST API also offers searching and querying through a parameter-based set of keywords:

  `/api/<resource-name>?param=value&param=value...`

  where

  Parameter Description Examples
  filter Used to return only the set of resources that have an attribute matching a certain value ?filter=name= \"my-resource \"

  ?filter=people/gender!= \"male \" and people/age>=21

  ?filter=application-name ^= \"my-app- \"
  orderby To order the returned resources by the specified attribute ?orderby=created:desc

  ?orderby=people/surname:asc

  aggregation On top of the requested resources, it will also return on-the-fly aggregations based on the specified function. Available functions: avg, max, min, sum, cardinality, terms, stats, extendedstats, percentiles, value_count, missing ?aggregation=avg:people/age
  last and first Returns a range of resources by setting the first and last (1-based) query parameters ?first=10&last=20

  select Selects only certain attributes to be returned by the server. Avoiding sending information that will not be useful reduces the load on the network and the server ?select=people/id

  ## Resources
  Resources are managed individually, which means that the data schemas and available operations might defer from one to the other. These options are all explained and exemplified in the following sections.")


(defn openapi-spec
  "Returns the OpenAPI spec for the given resource types."
  [resources-metadata]
  (let [nuvla-api-spec {:openapi      "3.0.3"
                        :info         {:title          "Nuvla API"
                                       :description    nuvla-api-description
                                       :termsOfService "https://docs.nuvla.io/legal/legal/"
                                       :contact        {:name  "SixSq Support",
                                                        :email "support@sixsq.com"}
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
                        :components   {:schemas         (schemas resources-metadata)
                                       :parameters      (merge (path-parameters)
                                                               (query-parameters))
                                       :securitySchemes (security-schemes)}
                        :security     [{:authnHeader []}
                                       {:cookieAuth []}]}]
    #_(with-redefs [spec-tools.impl/extract-form
                    (fn [spec]
                      (if (seq? spec) spec (openapi-schema/->openapi-schema spec)))]
        (openapi/openapi-spec nuvla-api-spec))
    nuvla-api-spec))


(def accept-resource-metadata (constantly true))


(defn nuvla-openapi-spec
  "Returns the OpenAPI spec for the Nuvla API."
  []
  (let [resources-metadata
        (->> (vals @resource-metadata/templates)
             (filter accept-resource-metadata))
        ;; TODO: figure out why crud/query-as-admin returns 0 results
        ;; to avoid accessing the templates atom directly as done above
        #_(crud/query-as-admin "resource-metadata" {:cimi-params {}})]
    (openapi-spec resources-metadata)))



#_(defn target-spec
    "Generates the ideal OpenAPI spec for the Nuvla API for
     a few selected resources.
     Parts of the info is generated, parts is hard-coded.
     To be seen as a target spec to try to converge to."
    []
    (with-redefs [accept-resource-metadata
                        (comp #{"resource-metadata/session"
                                "resource-metadata/session-template"
                                "resource-metadata/module-application"
                                "resource-metadata/deployment"
                                "resource-metadata/deployment-parameter"}
                              :id)
                  paths target-paths]
      (nuvla-openapi-spec)))


(def accept-session-email
  (comp #{"resource-metadata/session"
          "resource-metadata/email"}
        :id))


(def session-paths
  (constantly
    {"/session" {:post {:security    [{}]                   ;; authn not needed to call /session endpoints
                        :tags        ["session"],
                        :description "Creates a session",
                        :requestBody {:content  {"application/json"
                                                 {:schema   {:$ref "#/components/schemas/SessionCreateInput"}
                                                  :examples {"Password"
                                                             {:summary "Session password template"
                                                              :value   {"template" {"href"     "session-template/password"
                                                                                    "username" "test-user"
                                                                                    "password" "xyzk"}}}
                                                             "ApiKey"
                                                             {:summary "Session api-key template"
                                                              :value   {"template" {"href"   "session-template/api-key"
                                                                                    "key"    "test-api-key"
                                                                                    "secret" "xyzk"}}}}}},
                                      :required true},
                        :responses   {201 {:description "The session was created successfully",
                                           :content     {"application/json" {:schema {:$ref "#/components/schemas/Session"}}}},
                                      403 {:description "Not authorized"}}},
                 :get  {:security    [{}]                   ;; authn not needed to call /session endpoints
                        :tags        ["session"],
                        :description "Returns a collection of sessions",
                        :responses   {200 {:description "A collection of sessions",
                                           :content     {"application/json" {:schema {:$ref "#/components/schemas/SessionCollection"}}}},
                                      403 {:description "Not authorized"}}}
                 :put  {:security    [{}]                   ;; authn not needed to call /session endpoints
                        :tags        ["session"],
                        :description "Returns a collection of sessions",
                        :responses   {200 {:description "A collection of sessions",
                                           :content     {"application/json" {:schema {:$ref "#/components/schemas/SessionCollection"}}}},
                                      403 {:description "Not authorized"}}}}}))


(def email-paths
  (constantly
    {"/email"                       {:post   {:tags        ["email"],
                                              :description "Creates an email address",
                                              :requestBody {:content  {"application/json" {:schema {:$ref "#/components/schemas/EmailCreateInput"}}},
                                                            :required true},
                                              :responses   {201 {:description "The email address was created successfully",
                                                                 :content     {"application/json" {:schema {:$ref "#/components/schemas/Email"}}}},
                                                            403 {:description "Not authorized"}}},
                                     :get    {:tags        ["email"],
                                              :description "Returns a collection of email addresses",
                                              :parameters  query-parameters-refs
                                              :responses   {200 {:description "A collection of email addresses",
                                                                 :content     {"application/json" {:schema {:$ref "#/components/schemas/EmailCollection"}}}},
                                                            403 {:description "Not authorized"}}}
                                     :put    {:tags        ["email"],
                                              :description "Returns a collection of email addresses",
                                              :operationId "searchEmails"
                                              :parameters  query-parameters-refs
                                              :responses   {200 {:description "A collection of email addresses",
                                                                 :content     {"application/json" {:schema {:$ref "#/components/schemas/EmailCollection"}}}},
                                                            403 {:description "Not authorized"}}}
                                     :delete {:tags        ["email"]
                                              :description "Deletes a set of email addresses in bulk"
                                              :parameters  [{:name        "bulk"
                                                             :in          "header"
                                                             :required    true
                                                             :description "`bulk=true` header must be added for bulk operations"
                                                             :schema      {:type "boolean"}
                                                             :example     "true"}
                                                            (parameter-ref "bulkDeleteFilterParam")]
                                              :responses   {200 {:description "The email addresses were deleted successfully"
                                                                 ;; TODO: model the response
                                                                 ;; :body {
                                                                 ;;   :took 13,
                                                                 ;;   :deleted 1,
                                                                 ;;   :throttled_until_millis 0,
                                                                 ;;   :noops 0,
                                                                 ;;   :retries {:bulk 0, :search 0},
                                                                 ;;   :throttled_millis 0,
                                                                 ;;   :total 1,
                                                                 ;;   :timed_out false,
                                                                 ;;   :requests_per_second -1.0,
                                                                 ;;   :batches 1,
                                                                 ;;   :version_conflicts 0,
                                                                 ;;   :failures []}}
                                                                 },
                                                            403 {:description "Not authorized"},
                                                            404 {:description "The resource was not found"}}}}
     "/email/{resourceId}"          {:parameters [resource-id-parameter-ref]
                                     :get        {:tags        ["email"],
                                                  :description "Returns an email address given the id",
                                                  :responses   {200 {:description "The email address was retrieved successfully",
                                                                     :content     {"application/json" {:schema {:$ref "#/components/schemas/Email"}}}},
                                                                403 {:description "Not authorized"}
                                                                404 {:description "The resource was not found"}}},
                                     :delete     {:tags        ["email"]
                                                  :description "Deletes an email address"
                                                  :responses   {200 {:description "The email address was deleted successfully"},
                                                                403 {:description "Not authorized"},
                                                                404 {:description "The resource was not found"}}}}
     "/email/{resourceId}/validate" {:parameters [resource-id-parameter-ref]
                                     :post       {:tags        ["email"],
                                                  :description "Starts the workflow to validate the email address",
                                                  :responses   {200 {:description "The workflow was started successfully"},
                                                                403 {:description "Not authorized"}}}}}))


(defn collection-schema
  [item-schema-name]
  {:type       "object"
   :properties {"count"         {:type        "integer"
                                 :description "Total number of items"
                                 :title       "Count"}
                "resource-type" {:type        "string"
                                 :description "The collection items type"
                                 :title       "Items type"}
                "id"            {:description "unique resource identifier",
                                 :type        "string",
                                 :title       "id"}
                "acl"           (schema-ref "AclCollection")
                "resources"     {:type        "array",
                                 :items       (schema-ref item-schema-name),
                                 :title       "resources",
                                 :description "items"}
                "operations"    {:description "list of authorized resource operations",
                                 :type        "array",
                                 :title       "operations",
                                 :items       {:type        "object",
                                               :properties  {"href" {:type        "string",
                                                                     :description "URI for operation",
                                                                     :title       "href"},
                                                             "rel"  {:type        "string",
                                                                     :description "URL for performing action",
                                                                     :title       "rel"}},
                                               :title       "operation",
                                               :description "operation definition (name, URL) for a resource"}}}})


(def common-schemas
  (constantly
    {"Acl"           {:type        "object",
                      :properties  {"edit-meta" {:type        "array",
                                                 :items       {:type        "string",
                                                               :title       "principal",
                                                               :description "unique identifier for a principal"},
                                                 :description "list of principals that can edit resource metadata",
                                                 :title       "edit-meta"},
                                    "edit-acl"  {:type        "array",
                                                 :items       {:type        "string",
                                                               :title       "principal",
                                                               :description "unique identifier for a principal"},
                                                 :description "list of principals that can edit resource ACL",
                                                 :title       "edit-acl"},
                                    "delete"    {:type        "array",
                                                 :items       {:type        "string",
                                                               :title       "principal",
                                                               :description "unique identifier for a principal"},
                                                 :description "list of principals that can delete a resource",
                                                 :title       "delete"},
                                    "view-data" {:type        "array",
                                                 :items       {:type        "string",
                                                               :title       "principal",
                                                               :description "unique identifier for a principal"},
                                                 :description "list of principals that can view resource data",
                                                 :title       "view-data"},
                                    "view-meta" {:type        "array",
                                                 :items       {:type        "string",
                                                               :title       "principal",
                                                               :description "unique identifier for a principal"},
                                                 :description "list of principals that can view resource metadata",
                                                 :title       "view-meta"},
                                    "owners"    {:type        "array",
                                                 :items       {:type        "string",
                                                               :title       "principal",
                                                               :description "unique identifier for a principal"},
                                                 :title       "owners",
                                                 :description "list of owners of a resource"},
                                    "edit-data" {:type        "array",
                                                 :items       {:type        "string",
                                                               :title       "principal",
                                                               :description "unique identifier for a principal"},
                                                 :description "list of principals that can edit resource data",
                                                 :title       "edit-data"},
                                    "manage"    {:type        "array",
                                                 :items       {:type        "string",
                                                               :title       "principal",
                                                               :description "unique identifier for a principal"},
                                                 :description "list of principals that can manage a resource via custom actions",
                                                 :title       "manage"},
                                    "view-acl"  {:type        "array",
                                                 :items       {:type        "string",
                                                               :title       "principal",
                                                               :description "unique identifier for a principal"},
                                                 :description "list of principals that can view resource ACL",
                                                 :title       "view-acl"}},
                      :description "resource ACL",
                      :title       "Acl"}
     "AclCollection" {:type        "object",
                      :properties  {"query"       {:type        "array",
                                                   :items       {:type        "string",
                                                                 :title       "principal",
                                                                 :description "unique identifier for a principal"},
                                                   :description "list of principals that can query a collection",
                                                   :title       "query"},
                                    "add"         {:type        "array",
                                                   :items       {:type        "string",
                                                                 :title       "principal",
                                                                 :description "unique identifier for a principal"},
                                                   :description "list of principals that can add a new resource to a collection",
                                                   :title       "add"},
                                    "bulk-delete" {:type        "array",
                                                   :items       {:type        "string",
                                                                 :title       "principal",
                                                                 :description "unique identifier for a principal"},
                                                   :description "list of principals that can bulk delete to a collection",
                                                   :title       "bulk-delete"}},
                      :description "resource ACL",
                      :title       "AclCollection"}
     "CommonAttrs"   {:type       "object"
                      :properties {"id"                {:description "unique resource identifier",
                                                        :type        "string",
                                                        :title       "id"}
                                   "resource-type"     {:type        "string",
                                                        :description "resource type identifier",
                                                        :title       "resource-type"}
                                   "created"           {:description "creation timestamp (UTC) for resource",
                                                        :type        "string",
                                                        :title       "created",
                                                        :format      "date-time"}
                                   "updated"           {:description "latest resource update timestamp (UTC)",
                                                        :type        "string",
                                                        :title       "updated",
                                                        :format      "date-time"}
                                   "acl"               (schema-ref "Acl")
                                   "name"              {:type        "string",
                                                        :description "short, human-readable name for resource",
                                                        :title       "name"}
                                   "description"       {:type        "string",
                                                        :description "human-readable description of resource",
                                                        :title       "description"}
                                   "tags"              {:type        "array",
                                                        :items       {:type "string"},
                                                        :title       "tags",
                                                        :description "client defined tags of the resource"}
                                   "parent"            {:type        "string",
                                                        :description "reference to parent resource",
                                                        :title       "parent"}
                                   "resource-metadata" {:description "reference to the resource's metadata",
                                                        :format      "uuid",
                                                        :type        "string",
                                                        :title       "resource-metadata"}
                                   "operations"        {:description "list of authorized resource operations",
                                                        :type        "array",
                                                        :title       "operations",
                                                        :items       {:type        "object",
                                                                      :properties  {"href" {:type        "string",
                                                                                            :description "URI for operation",
                                                                                            :title       "href"},
                                                                                    "rel"  {:type        "string",
                                                                                            :description "URL for performing action",
                                                                                            :title       "rel"}},
                                                                      :title       "operation",
                                                                      :description "operation definition (name, URL) for a resource"}}
                                   "created-by"        {:type        "string",
                                                        :description "user id who created the resource",
                                                        :title       "created-by"},
                                   "updated-by"        {:type        "string",
                                                        :description "user id who updated the resource",
                                                        :title       "updated-by"}}}}))


(def session-schemas
  (constantly
    {"Session"                 {:allOf [(schema-ref "CommonAttrs")
                                        {:type       "object",
                                         :properties {"redirect-url" {:type        "string",
                                                                      :description "redirect URI to be used on success",
                                                                      :title       "redirect-url"},
                                                      "server"       {:type        "string",
                                                                      :title       "server",
                                                                      :description "string containing something other than only whitespace"},
                                                      "active-claim" {:type        "string",
                                                                      :title       "nonblank-string",
                                                                      :description "string containing something other than only whitespace"},
                                                      "method"       {:type        "string",
                                                                      :description "authentication method",
                                                                      :title       "method"},
                                                      "user"         {:type        "string",
                                                                      :title       "user",
                                                                      :description "string containing something other than only whitespace"},
                                                      "client-ip"    {:type        "string",
                                                                      :title       "client-ip",
                                                                      :description "string containing something other than only whitespace"},
                                                      "identifier"   {:type "string"},,
                                                      "template"     {:type       "object",
                                                                      :properties {"href" {:type "string"}},
                                                                      :title      "template"},
                                                      "expiry"       {:title       "expiry",
                                                                      :description "UTC timestamp",
                                                                      :type        "string",
                                                                      :format      "date-time"},
                                                      "roles"        {:type        "string",
                                                                      :title       "roles",
                                                                      :description "string containing something other than only whitespace"},
                                                      "groups"       {:type        "string",
                                                                      :title       "groups",
                                                                      :description "string containing something other than only whitespace"}},
                                         :title      "Session"}]}
     "SessionCollection"       (collection-schema "Session")
     "SessionTemplatePassword" {:type       "object"
                                :properties {"href"     {:type  "string"
                                                         :enum  ["session-template/password"]
                                                         :title "href"}
                                             "username" {:type        "string"
                                                         :description "The username"
                                                         :title       "Username"}
                                             "password" {:type        "string"
                                                         :description "The user password"
                                                         :title       "Password"}}}
     "SessionTemplateApiKey"   {:type       "object"
                                :properties {"href"   {:type  "string"
                                                       :enum  ["session-template/api-key"]
                                                       :title "href"}
                                             "key"    {:type        "string"
                                                       :description "The api key"
                                                       :title       "Key"}
                                             "secret" {:type        "string"
                                                       :description "The api client secret"
                                                       :title       "Secret"}}}
     "SessionTemplate"         {:oneOf         [(schema-ref "SessionTemplatePassword")
                                                (schema-ref "SessionTemplateApiKey")]
                                :discriminator {:propertyName "href"
                                                :mapping      {"session-template/password" "#/components/schemas/SessionTemplatePassword"
                                                               "session-template/api-key"  "#/components/schemas/SessionTemplateApiKey"}}}
     "SessionCreateInput"      {:type       "object",
                                :properties {"template" (schema-ref "SessionTemplate")}
                                :title      "SessionCreateInput"}}))


(def email-schemas
  (constantly
    {"EmailBase"        {:type       "object",
                         :properties {"address"   {:type        "string",
                                                   :format      "email"
                                                   :description "Email address",
                                                   :title       "address"},
                                      "validated" {:type        "boolean",
                                                   :title       "validated",
                                                   :description "validated email address?"}},}
     "Email"            {:allOf [(schema-ref "CommonAttrs")
                                 (schema-ref "EmailBase")]
                         :title "Email"}
     "EmailCollection"  (collection-schema "Email")
     "EmailCreateInput" (schema-ref "EmailBase")}))


(defn custom-descriptions
  []
  (with-redefs [accept-resource-metadata accept-session-email
                tags                     (constantly [{:name        "session"
                                                       :description "Most endpoints in the Nuvla api require the caller to provide a reference to an active session.
                                      To create a session, the following types of authentication are supported:
                                      - username/password
                                      - api key (recommended for programmatic use)
                                      - ..."}])
                paths                    (constantly
                                           (merge
                                             (session-paths)
                                             (email-paths)))
                schemas                  (into (sorted-map)
                                               (constantly
                                                 (concat
                                                   (common-schemas)
                                                   (session-schemas)
                                                   (email-schemas))))]
    (nuvla-openapi-spec)))


(defn generated-descriptions
  []
  (with-redefs [accept-resource-metadata accept-session-email
                paths                    (constantly
                                           (merge
                                             (session-paths)
                                             (email-paths)))
                schemas                  (constantly
                                           (into (sorted-map)
                                                 (merge
                                                   (common-schemas)
                                                   (session-schemas)
                                                   (email-schemas))))]
    (nuvla-openapi-spec)))


(comment
  (let [api-spec (first [generated-descriptions
                         custom-descriptions
                         nuvla-openapi-spec])]
    (require '[clojure.data.json :as json])
    (-> (api-spec)
        (json/pprint)))

  #_(keys @resource-metadata/templates))


