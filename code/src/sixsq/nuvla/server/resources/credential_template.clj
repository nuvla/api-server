(ns sixsq.nuvla.server.resources.credential-template
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
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-template :as ct]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


;; the templates are managed as in-memory resources, so modification
;; of the collection is not permitted, but anonymous credentials must be
;; able to list and view templates (if anonymous registration is
;; permitted)

(def collection-acl {:query ["group/nuvla-anon"]})


;;
;; atom to keep track of the loaded CredentialTemplate resources
;;
(def templates (atom {}))


(defn complete-resource
  "Completes the given document with server-managed information: resource-type,
   timestamps, and operations. NOTE: The subtype MUST provide an ACL for the
   template."
  [{:keys [method] :as resource}]
  (when method
    (let [id (str resource-type "/" method)]
      (-> resource
          (merge {:id            id
                  :resource-type resource-type})
          u/update-timestamps))))


(defn register
  "Registers a given CredentialTemplate resource with the server.
   The resource document (resource) must be valid.
   The template-id key must be provided; it will be used to generate the
   id of the form 'credential-template/template-id'."
  [resource]
  (when-let [{:keys [id] :as full-resource} (complete-resource resource)]
    (swap! templates assoc id full-resource)
    (log/info "loaded credential-template" id)))


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
  resource-type
  [resource]
  (validate-subtype resource))


;;
;; CRUD operations
;;

(defmethod crud/add resource-type
  [request]
  (throw (r/ex-bad-method request)))


(defmethod crud/retrieve resource-type
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (get @templates id)
          (a/throw-cannot-view request)
          (a/select-viewable-keys request)
          (r/json-response)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;; must override the default implementation so that the
;; data can be pulled from the atom rather than the database
(defmethod crud/retrieve-by-id resource-type
  [id]
  (try
    (get @templates id)
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/edit resource-type
  [request]
  (throw (r/ex-bad-method request)))


(defmethod crud/delete resource-type
  [request]
  (throw (r/ex-bad-method request)))


(defmethod crud/query resource-type
  [request]
  (a/throw-cannot-query collection-acl request)
  (let [wrapper-fn              (std-crud/collection-wrapper-fn resource-type collection-acl collection-type true false)
        entries                 (or (filter #(a/can-view? % request) (vals @templates)) [])
        updated-entries         (remove nil? (map #(a/select-viewable-keys % request) entries))
        ;; FIXME: At least the paging options should be supported.
        options                 (select-keys request [:user-id :claims :query-params :cimi-params])
        count-before-pagination (count updated-entries)
        wrapped-entries         (wrapper-fn request updated-entries)
        entries-and-count       (assoc wrapped-entries :count count-before-pagination)]
    (r/json-response entries-and-count)))


;;
;; initialization: create metadata for this collection
;;

(defn initialize
  []
  (md/register (gen-md/generate-metadata ::ns ::ct/schema)))

