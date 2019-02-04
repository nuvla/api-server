(ns com.sixsq.slipstream.ssclj.resources.credential-template
  "
A collection of templates that are used to create a variety of credentials.

**NOTE**: CredentialTemplate resources are in-memory resources and
consequently do **not** support the CIMI filtering parameters.

SlipStream must manage a variety of credentials to provide, for example,
programmatic access to SlipStream or SSH access to virtual machines running on
a cloud infrastructure. The CredentialTemplate resources correspond to the
various methods that can be used to create these resources.

The parameters required can be found within each template, using the standard
CIMI read pattern. Details for each parameter can be found by invoking looking
at the ResourceMetadata resource for the type.

Template | Credential | Description
-------- | ---------- | -----------
import-ssh-public-key | ssh-public-key | imports an SSH public key from an existing key pair
generate-ssh-key-pair | ssh-public-key | generates a new SSH key pair, storing public key and returning private key
generate-api-key | api-key | generates API key and secret, storing secret digest and returning secret
cloud* | cloud-cred-* | credentials specific to particular cloud infrastructures

Typically, there will also be Credential Template resources that describe the
credentials for each supported cloud infrastructure.

```shell
# List all of the credential creation mechanisms
# NOTE: You must be authenticated.  Add the appropriate
# cookie options to the curl command.
#
curl https://nuv.la/api/credential-template
```
"
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.slipstream.auth.acl :as a]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.resource-metadata :as md]
    [com.sixsq.slipstream.ssclj.resources.spec.credential-template :as ct]
    [com.sixsq.slipstream.ssclj.util.metadata :as gen-md]
    [com.sixsq.slipstream.util.response :as r]))

(def ^:const resource-tag :credentialTemplates)

(def ^:const resource-name "CredentialTemplate")

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "CredentialTemplateCollection")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def ^:const collection-uri (str c/slipstream-schema-uri collection-name))

;; the templates are managed as in-memory resources, so modification
;; of the collection is not permitted, but anonymous credentials must be
;; able to list and view templates (if anonymous registration is
;; permitted)
(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "ANON"
                              :type      "ROLE"
                              :right     "VIEW"}
                             {:principal "USER"
                              :type      "ROLE"
                              :right     "VIEW"}]})

;;
;; atom to keep track of the loaded CredentialTemplate resources
;;
(def templates (atom {}))
(def descriptions (atom {}))


(defn collection-wrapper-fn
  "Specialized version of this function that removes the adding
   of operations to the collection and entries.  These are already
   part of the stored resources."
  [resource-name collection-acl collection-uri collection-key]
  (fn [request entries]
    (let [skeleton {:acl         collection-acl
                    :resourceURI collection-uri
                    :id          (u/de-camelcase resource-name)}]
      (-> skeleton
          (crud/set-operations request)
          (assoc collection-key entries)))))


(defn complete-resource
  "Completes the given document with server-managed information: resourceURI,
   timestamps, and operations. NOTE: The subtype MUST provide an ACL for the
   template."
  [{:keys [method] :as resource}]
  (when method
    (let [id (str resource-url "/" method)
          href (str id "/describe")
          ops [{:rel (:describe c/action-uri) :href href}]]
      (-> resource
          (merge {:id          id
                  :resourceURI resource-uri
                  :operations  ops})
          u/update-timestamps))))


(defn register
  "Registers a given CredentialTemplate resource and its description with the
   server. The resource document (resource) and the description (desc) must be
   valid. The template-id key must be provided; it will be used to generate the
   id of the form 'credential-template/template-id'."
  [resource desc]
  (when-let [{:keys [id] :as full-resource} (complete-resource resource)]
    (swap! templates assoc id full-resource)
    (log/info "loaded CredentialTemplate" id)
    (when desc
      (let [acl (:acl full-resource)
            full-desc (assoc desc :acl acl)]
        (swap! descriptions assoc id full-desc))
      (log/info "loaded CredentialTemplate description" id))))


;;
;; schemas
;;

(def CredentialTemplateDescription
  (merge c/CommonParameterDescription
         {:type   {:displayName "Credential Type"
                   :category    "general"
                   :description "type of credential"
                   :type        "string"
                   :mandatory   true
                   :readOnly    true
                   :order       10}
          :method {:displayName "Credential Creation Method"
                   :category    "general"
                   :description "method for creating credential"
                   :type        "string"
                   :mandatory   true
                   :readOnly    true
                   :order       11}}))


;;
;; multimethods for validation
;;

(defmulti validate-subtype
          "Validates the given resource against the specific
           CredentialTemplate method."
          :method)


(defmethod validate-subtype :default
  [resource]
  (throw (ex-info (str "unknown CredentialTemplate method: " (:method resource)) resource)))


(defmethod crud/validate
  resource-uri
  [resource]
  (validate-subtype resource))


;;
;; CRUD operations
;;

(defmethod crud/add resource-name
  [request]
  (throw (r/ex-bad-method request)))


(defmethod crud/retrieve resource-name
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-url "/" uuid)]
      (-> (get @templates id)
          (a/can-view? request)
          (r/json-response)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;; must override the default implementation so that the
;; data can be pulled from the atom rather than the database
(defmethod crud/retrieve-by-id resource-url
  [id]
  (try
    (get @templates id)
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/edit resource-name
  [request]
  (throw (r/ex-bad-method request)))


(defmethod crud/delete resource-name
  [request]
  (throw (r/ex-bad-method request)))


(defn- viewable? [request {:keys [acl] :as entry}]
  (try
    (a/can-view? {:acl acl} request)
    (catch Exception _
      false)))


(defmethod crud/query resource-name
  [request]
  (a/can-view? {:acl collection-acl} request)
  (let [wrapper-fn (collection-wrapper-fn resource-name collection-acl collection-uri resource-tag)
        entries (or (filter (partial viewable? request) (vals @templates)) [])
        ;; FIXME: At least the paging options should be supported.
        options (select-keys request [:identity :query-params :cimi-params :credential-name :credential-roles])
        count-before-pagination (count entries)
        wrapped-entries (wrapper-fn request entries)
        entries-and-count (assoc wrapped-entries :count count-before-pagination)]
    (r/json-response entries-and-count)))


;;
;; actions
;;

(defmethod crud/do-action [resource-url "describe"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-url "/" uuid)]
      (-> (get @descriptions id)
          (a/can-view? request)
          (r/json-response)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; initialization: create metadata for this collection
;;

(defn initialize
  []
  (md/register (gen-md/generate-metadata ::ns ::ct/schema)))

