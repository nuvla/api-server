(ns sixsq.nuvla.server.resources.api-docs
  "
The `openapi` resources provide an OpenAPI spec for the Nuvla api.
The only action allowed is `query` on the collection, which will return the
full spec for the Nuvla API.
"
  (:require
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.api-docs :as api-docs]
    [sixsq.nuvla.server.resources.api-docs.openapi :as utils]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-anon"]})


;;
;; CRUD operations
;;

(defn query-impl
  [_request]
  (-> (utils/nuvla-openapi-spec)
      r/json-response))


(defmethod crud/query resource-type
  [request]
  (query-impl request))

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::api-docs/schema))


(defmethod crud/validate
  resource-type
  [resource]
  (validate-fn resource))


;;
;; initialization: no schema for this parent resource
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::api-docs/schema))

(defn initialize
  []
  ;; FIXME: std-crud/initialize needed?
  #_(std-crud/initialize resource-type nil)
  (md/register resource-metadata))
