(ns sixsq.nuvla.server.resources.email
  "
This resource corresponds to an email address. The resource contains only the
common attributes, a syntactically valid email address, and a boolean flag that
indicates if the email address has been validated.

When the address has not been validated, a 'validate' action is provided. This
will send an email to the user with a callback URL to validate the email
address. When the callback is triggered, the `validated` flag is set to true.
"
  (:require
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.email :as email]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.util.response :as r]
    [superstring.core :as str]))

(def ^:const resource-type (u/ns->type *ns*))

(def ^:const resource-name "Email")

(def ^:const resource-tag (keyword (str (str/camel-case resource-name) "s")))

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "EmailCollection")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def ^:const collection-uri (str c/slipstream-schema-uri collection-name))

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "USER"
                              :type      "ROLE"
                              :right     "MODIFY"}]})

(def actions [{:name "validate"
               :uri (:validate c/action-uri)
               :description "starts the workflow to validate the email address"
               :method "POST"
               :inputMessage "application/json"
               :outputMessage "application/json"}])


;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-uri
  [resource request]
  (a/add-acl (dissoc resource :acl) request))

;;
;; "Implementations" of multimethod declared in crud namespace
;;

;; resource identifier is the MD5 checksum of the email address
(defmethod crud/new-identifier resource-name
  [resource resource-name]
  (if-let [new-id (some-> resource :address u/md5)]
    (assoc resource :id (str (u/de-camelcase resource-name) "/" new-id))))


(def validate-fn (u/create-spec-validation-fn ::email/schema))
(defmethod crud/validate
  resource-uri
  [resource]
  (validate-fn resource))


(def add-impl (std-crud/add-fn resource-name collection-acl resource-uri))

(defmethod crud/add resource-name
  [request]
  (add-impl (assoc-in request [:body :validated] false)))


(def retrieve-impl (std-crud/retrieve-fn resource-name))

(defmethod crud/retrieve resource-name
  [request]
  (retrieve-impl request))


(def delete-impl (std-crud/delete-fn resource-name))

(defmethod crud/delete resource-name
  [request]
  (delete-impl request))

;;
;; available operations; disallows editing of resource, adds validate action for unvalidated emails
;;

(defmethod crud/set-operations resource-uri
  [{:keys [validated] :as resource} request]
  (try
    (a/can-modify? resource request)
    (let [href (:id resource)
          ^String resourceURI (:resourceURI resource)
          ops (if (.endsWith resourceURI "Collection")
                [{:rel (:add c/action-uri) :href href}]
                (cond-> [{:rel (:delete c/action-uri) :href href}]
                        (not validated) (conj {:rel (:validate c/action-uri) :href (str href "/validate")})))]
      (assoc resource :operations ops))
    (catch Exception _
      (dissoc resource :operations))))

;;
;; collection
;;

(def query-impl (std-crud/query-fn resource-name collection-acl collection-uri resource-tag))

(defmethod crud/query resource-name
  [request]
  (query-impl request))


;;
;; actions
;;

(defmethod crud/do-action [resource-url "validate"]
  [{{uuid :uuid} :params baseURI :baseURI}]
  (let [id (str resource-url "/" uuid)]
    (when-let [{:keys [address validated]} (crud/retrieve-by-id-as-admin id)]
      (if-not validated
        (try
          (-> (email-utils/create-callback id baseURI)
              (email-utils/send-validation-email address))
          (r/map-response "check your mailbox for a validation message" 202)
          (catch Exception e
            (.printStackTrace e)))
        (throw (r/ex-bad-request "email address is already validated"))))))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize resource-url ::email/schema)
  (md/register (gen-md/generate-metadata ::ns ::email/schema)))
