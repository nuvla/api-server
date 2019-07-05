(ns sixsq.nuvla.server.resources.user-template
  "
The `user-template` resources define the 'user registration' methods that are
permitted by the server. The user-template collection follows all of the
standard SCRUD patterns.

The server will always contain the 'username-password' user template. This
template is only visible to administrators and allows the direct creation of a
new user without any email verification, etc.

The system administrator may create additional templates to allow other user
registration methods. If the ACL of the template allows for 'anonymous' access,
then the server will support self-registration of users. The registration
processes will typically require additional validation step, such as email
verification.
"
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils.acl :as acl-utils]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.user-template :as user-tpl]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def resource-acl (acl-utils/normalize-acl {:owners   ["group/nuvla-admin"]
                                            :view-acl ["group/nuvla-anon"]}))


(def desc-acl (acl-utils/normalize-acl {:owners   ["group/nuvla-admin"]
                                        :view-acl ["group/nuvla-anon"]}))


(def collection-acl {:query ["group/nuvla-anon"]
                     :add   ["group/nuvla-admin"]})


;;
;; atom to keep track of the available user-template methods
;;

(def known-method (atom #{}))


(defn register
  "Registers a given user-template method with the server."
  [method]
  (when method
    (swap! known-method conj method)
    (log/info "loaded user-template method " method)))

;;
;; multimethods for validation
;;

(defmulti validate-subtype
          "Validates the given resource against the specific user-template
           subtype schema."
          :method)


(defmethod validate-subtype :default
  [resource]
  (throw (ex-info (str "unknown user-template method: " (:method resource)) resource)))


(defmethod crud/validate
  resource-type
  [resource]
  (validate-subtype resource))


;;
;; identifiers for these resources are the same as the :instance value
;;

(defmethod crud/new-identifier resource-type
  [{:keys [instance] :as resource} resource-name]
  (->> instance
       (str resource-type "/")
       (assoc resource :id)))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [{{:keys [method]} :body :as request}]
  (if (@known-method method)
    (add-impl request)
    (throw (r/ex-bad-request (str "invalid registration method '" method "'")))))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [request]
  (edit-impl request))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


;;
;; initialization: create metadata for this collection
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::user-tpl/schema))


(defn initialize
  []
  (md/register resource-metadata))

