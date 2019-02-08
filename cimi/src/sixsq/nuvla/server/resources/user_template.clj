(ns sixsq.nuvla.server.resources.user-template
  "
UserTemplate resources define the 'user registration' methods that are
permitted by the server. The UserTemplate collection follows all of the CIMI
SCRUD patterns.

The server will always contain the 'direct user template. This template is
only acceptable to administrators and allows the direct creation of a new user
without any email verification, etc.

The system administrator may create additional templates to allow other user
registration methods. If the ACL of the template allows for 'anonymous' access,
then the server will support self-registration of users. The registration
processes will typically require additional validation step, such as email
verification.

Listing of the available UserTemplate resources on Nuvla.

```shell
curl 'https://nuv.la/api/user-template?select=name,description'
```

```json
{
  \"count\" : 15,
  ...
  \"resource-type\" : \"http://sixsq.com/slipstream/1/UserTemplateCollection\",
  \"id\" : \"user-template\",
  \"userTemplates\" : [ {
    \"name\" : \"ESRF Realm\",
    \"description\" : \"Creates a new user through OIDC registration\",
    ...
    },
    \"resource-type\" : \"http://sixsq.com/slipstream/1/UserTemplate\"
  }, {
    \"name\" : \"INFN Realm\",
    \"description\" : \"Creates a new user through OIDC registration\",
    ...

```
"
  (:require
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.user-template :as user-tpl]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.util.response :as r]))

(def ^:const resource-type (u/ns->type *ns*))

(def ^:const collection-name "UserTemplateCollection")

(def ^:const resource-uri resource-type)

(def ^:const collection-uri collection-name)

(def resource-acl {:owner {:principal "ADMIN"
                           :type      "ROLE"}
                   :rules [{:principal "ADMIN"
                            :type      "ROLE"
                            :right     "ALL"}
                           {:principal "ANON"
                            :type      "ROLE"
                            :right     "VIEW"}
                           {:principal "USER"
                            :type      "ROLE"
                            :right     "VIEW"}]})

(def desc-acl {:owner {:principal "ADMIN"
                       :type      "ROLE"}
               :rules [{:principal "ANON"
                        :type      "ROLE"
                        :right     "VIEW"}]})

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "ANON"
                              :type      "ROLE"
                              :right     "VIEW"}
                             {:principal "USER"
                              :type      "ROLE"
                              :right     "VIEW"}]})


;;
;; atom to keep track of the UserTemplate descriptions
;;

(def known-method (atom #{}))


(defn register
  "Registers a given UserTemplate id with the server."
  [method]
  (when method
    (swap! known-method conj method)
    (log/info "loaded UserTemplate description" method)))

;;
;; multimethods for validation
;;

(defmulti validate-subtype
          "Validates the given resource against the specific
           UserTemplate subtype schema."
          :method)


(defmethod validate-subtype :default
  [resource]
  (throw (ex-info (str "unknown UserTemplate type: " (:method resource)) resource)))


(defmethod crud/validate
  resource-uri
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

(def add-impl (std-crud/add-fn resource-type collection-acl resource-uri))

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


(def query-impl (std-crud/query-fn resource-type collection-acl collection-uri))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


;;
;; initialization: create metadata for this collection
;;
(defn initialize
  []
  (md/register (gen-md/generate-metadata ::ns ::user-tpl/schema)))

