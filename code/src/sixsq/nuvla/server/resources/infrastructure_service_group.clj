(ns sixsq.nuvla.server.resources.infrastructure-service-group
  "
The infrastructure-service-group resource represents a group of
infrastructure-service resources, which are intended to be used together.

The resource contains metadata concerning the infrastructure-service-group and
an automatically generated list of associated infrastructure-service resources.
The resources are tied to an infrastructure via the infrastructure-service
resource's `parent` attribute, which will contain the `id` of the
infrastructure-service-group resource.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.middleware.cimi-params.impl :as cimi-params-impl]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-group :as infra-service-group]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})

;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize resource-type ::infra-service-group/schema)
  (md/register (gen-md/generate-metadata ::ns ::infra-service-group/schema)))


;;
;; multimethods for validation and operations
;;

(def validate-fn (u/create-spec-validation-fn ::infra-service-group/schema))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl (dissoc resource :acl) request))


;;
;; utilities for dealing with the automatic generation of
;; :infrastructure-services value
;;

(defn dissoc-services
  [{:keys [body] :as request}]
  (assoc request :body (dissoc body :infrastructure-services)))


(defn service-query
  ([resource-id]
   (service-query {:nuvla/authn auth/internal-identity} resource-id))
  ([initial-request resource-id]
   (let [filter (-> {:filter (str "parent='" resource-id "'")}
                    (cimi-params-impl/cimi-filter))
         request (-> initial-request
                     (assoc :params {:resource-name infra-service/resource-type}
                            :route-params {:resource-name infra-service/resource-type}
                            :cimi-params {:filter filter
                                          :select ["id"]}))]
     (try
       (->> request
            crud/query
            :body
            :resources
            (map :id)
            (mapv (fn [id] {:href id})))
       (catch Exception _
         [])))))


(defn assoc-services
  [{:keys [body] :as response} {:keys [headers] :as request}]
  (assoc response :body (assoc body :infrastructure-services (service-query request (:id body)))))


;;
;; CRUD operations
;;


(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [request]
  (-> request dissoc-services add-impl))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (-> request retrieve-impl (assoc-services request)))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [request]
  (-> request dissoc-services edit-impl))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [{{:keys [resource-name uuid]} :route-params :as request}]
  (let [id (str resource-name "/" uuid)
        service-count (count (service-query id))]
    (if (zero? service-count)
      (delete-impl request)
      (throw (r/ex-response (str "cannot delete " id ": " service-count " linked services remain") 409)))))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [{:keys [resources] :as request}]
  (let [updated-service-groups (map #(assoc-services % request) resources)]
    (-> request query-impl (assoc :resources updated-service-groups))))
