(ns com.sixsq.nuvla.server.resources.email
  "
This resource corresponds to an email address. The resource contains only the
common attributes, a syntactically valid email address, and a boolean flag that
indicates if the email address has been validated.

When the address has not been validated, a 'validate' action is provided. This
will send an email to the user with a callback URL to validate the email
address. When the callback is triggered, the `validated` flag is set to true.
"
  (:require
    [clj-stacktrace.repl :as st]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.email.utils :as email-utils]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.email :as email]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]
    [com.sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query       ["group/nuvla-user"]
                     :add         ["group/nuvla-user"]
                     :bulk-delete ["group/nuvla-user"]})


(def actions [{:name           "validate"
               :uri            "validate"
               :description    "starts the workflow to validate the email address"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}])


;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl (dissoc resource :acl) request))

;;
;; "Implementations" of multimethod declared in crud namespace
;;

;; resource identifier a UUID generated from the email address
(defmethod crud/new-identifier resource-type
  [resource resource-name]
  (when-let [new-id (some-> resource :address u/from-data-uuid)]
    (assoc resource :id (str resource-name "/" new-id))))


(def validate-fn (u/create-spec-validation-fn ::email/schema))
(defmethod crud/validate
  resource-type
  [resource]
  (validate-fn resource))


(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defmethod crud/add resource-type
  [request]
  (add-impl (assoc-in request [:body :validated] false)))


(def retrieve-impl (std-crud/retrieve-fn resource-type))

(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def delete-impl (std-crud/delete-fn resource-type))

(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def bulk-delete-impl (std-crud/bulk-delete-fn resource-type collection-acl collection-type))

(defmethod crud/bulk-delete resource-type
  [request]
  (bulk-delete-impl request))

;;
;; available operations; disallows editing of resource, adds validate action for unvalidated emails
;;

(defmethod crud/set-operations resource-type
  [{:keys [id resource-type validated] :as resource} request]
  (try
    (a/can-edit? resource request)
    (let [ops (if (u/is-collection? resource-type)
                [(u/operation-map id :add)]
                (cond-> [(u/operation-map id :delete)]
                        (not validated) (conj (u/action-map id :validate))))]
      (assoc resource :operations ops))
    (catch Exception _
      (dissoc resource :operations))))

;;
;; collection
;;

(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))

(defmethod crud/query resource-type
  [request]
  (query-impl request))


;;
;; actions
;;

(defmethod crud/do-action [resource-type "validate"]
  [{{uuid :uuid} :params base-uri :base-uri}]
  (let [id (str resource-type "/" uuid)]
    (when-let [{:keys [address validated]} (crud/retrieve-by-id-as-admin id)]
      (if-not validated
        (try
          (-> (email-utils/create-callback id base-uri)
              (email-utils/send-validation-email address))
          (r/map-response "check your mailbox for a validation message" 202)
          (catch Exception e
            (str "email validation failed!" "\n" (st/pst-str e))))
        (throw (r/ex-bad-request "email address is already validated"))))))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::email/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::email/schema)
  (md/register resource-metadata))
