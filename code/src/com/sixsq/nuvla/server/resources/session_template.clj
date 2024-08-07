(ns com.sixsq.nuvla.server.resources.session-template
  "
A collection of templates that allow users to authenticate with the server by
creating a `session` resource. The concrete templates support a variety of
credentials and protocols.

Most Nuvla resources are only visible to authenticated users;
`session-template` resources are visible to anyone. The login process consists
of creating a `session` resource via the standard templated add pattern and
then using the returned token with subsequent interactions with the Nuvla
server.

The supported Python and Clojure(Script) libraries directly use the REST API
defined here for `session` management, but also provide higher-level functions
that simplify the authentication process.

Nuvla supports a wide variety of methods for authenticating with the server.
The `session-template` resources represent the supported authentication methods
for a given Nvula server. To list all the configured authentication
mechanisms for the server:

```shell
curl https://nuv.la/api/session-template
```

The Nuvla **administrator** defines the available methods by creating
`session-template` resources on the server via the standard 'add' pattern (and
in most cases an associated `configuration` resource). These can also be
'edited' and 'deleted' by the administrator.

**All users (including anonymous users)** can list the `session-template`
resources to discover supported authentication methods.

One `session-template` resource that will always exist on the server is the
'session-template/password' resource. This allows logging into the server with
a username and password pair stored in Nuvla's internal database.
"
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.auth.utils.acl :as acl-utils]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.session-template :as session-tpl]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]
    [com.sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def resource-acl (acl-utils/normalize-acl {:owners   ["group/nuvla-admin"]
                                            :view-acl ["group/nuvla-anon"]}))


(def collection-acl {:query ["group/nuvla-anon"]
                     :add   ["group/nuvla-admin"]})


;;
;; atom to keep track of the SessionTemplate descriptions
;;
(def known-methods (atom #{}))

(defn register
  "Registers a given SessionTemplate id with the server. The
   description (desc) must be valid. The authentication method must be used as
   the id. The description can be looked up via the id, e.g. 'internal'."
  [id]
  (when id
    (swap! known-methods conj id)
    (log/info "loaded SessionTemplate description" id)))

;;
;; multimethods for validation
;;

(defmulti validate-subtype
          "Validates the given resource against the specific
           SessionTemplate subtype schema."
          :method)

(defmethod validate-subtype :default
  [resource]
  (throw (ex-info (str "unknown SessionTemplate type: " (:method resource)) resource)))

(defmethod crud/validate
  resource-type
  [resource]
  (validate-subtype resource))

;;
;; identifiers for these resources are the same as the :instance value
;;
(defmethod crud/new-identifier resource-type
  [{:keys [instance method] :as resource} _resource-name]
  (let [new-id (if (= method instance)
                 instance
                 (str method "-" instance))]
    (assoc resource :id (str resource-type "/" new-id))))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defmethod crud/add resource-type
  [{{:keys [method]} :body :as request}]
  (if (@known-methods method)
    (add-impl request)
    (throw (r/ex-bad-request (str "invalid authentication method '" method "'")))))

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

(def resource-metadata (gen-md/generate-metadata ::ns ::session-tpl/schema))


(defn initialize
  []
  (md/register resource-metadata))

