(ns com.sixsq.slipstream.ssclj.resources.session-template
  "
A collection of templates that allow users to authenticate with the server by
creating a Session resource. The concrete templates support a variety of
credentials and protocols.

Most SlipStream resources are only visible to authenticated users. The login
process consists of creating a Session resource via the standard CIMI templated
add pattern and then using the returned token with subsequent interactions with
the SlipStream server.

The supported Python and Clojure(Script) libraries directly use the REST API
defined here for Session management, but also provide higher-level functions
that simplify the authentication process.


SlipStream supports a wide variety of methods for authenticating with the
server. The SessionTemplate resources represent the supported authentication
methods for a given SlipStream server. To list all the configured
authentication mechanism for the server:

```shell
curl https://nuv.la/api/session-template
```

The SlipStream **administrator** defines the available methods by creating
SessionTemplate resources on the server via the standard CIMI 'add' pattern
(and in most cases an associated Configuration resource). These can also be
'edited' and 'deleted' by the SlipStream administrator.

**All users (including anonymous users)** can list the SessionTemplates to
discover supported authentication methods.

One SessionTemplate that will always exist on the server is the
'session-template/internal' resource. This allows logging into the server with
a username and password pair stored in SlipStream's internal database.
"
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.slipstream.auth.acl :as a]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.ui-hints :as hints]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.resource-metadata :as md]
    [com.sixsq.slipstream.ssclj.resources.spec.session-template :as session-tpl]
    [com.sixsq.slipstream.ssclj.util.metadata :as gen-md]
    [com.sixsq.slipstream.util.response :as r]))

(def ^:const resource-tag :sessionTemplates)

(def ^:const resource-name "SessionTemplate")

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "SessionTemplateCollection")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def ^:const collection-uri (str c/slipstream-schema-uri collection-name))

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
;; atom to keep track of the SessionTemplate descriptions
;;
(def descriptions (atom {}))

(defn register
  "Registers a given SessionTemplate description with the server. The
   description (desc) must be valid. The authentication method must be used as
   the id. The description can be looked up via the id, e.g. 'internal'."
  [id desc]
  (when (and id desc)
    (let [full-desc (assoc desc :acl desc-acl)]
      (swap! descriptions assoc id full-desc))
    (log/info "loaded SessionTemplate description" id)))

;;
;; schemas
;;

(def SessionTemplateDescription
  (merge c/CommonParameterDescription
         hints/UIHintsParameterDescription
         {:method      {:displayName "Authentication Method"
                        :category    "general"
                        :description "method to be used to authenticate user"
                        :type        "string"
                        :mandatory   true
                        :readOnly    true
                        :order       10}
          :instance    {:displayName "Authentication Method Key (Name)"
                        :category    "general"
                        :description "key used to identify this authentication source"
                        :type        "string"
                        :mandatory   true
                        :readOnly    true
                        :order       11}}))
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
  resource-uri
  [resource]
  (validate-subtype resource))

;;
;; identifiers for these resources are the same as the :instance value
;;
(defmethod crud/new-identifier resource-name
  [{:keys [instance method] :as resource} resource-name]
  (let [new-id (if (= method instance)
                 instance
                 (str method "-" instance))]
    (assoc resource :id (str resource-url "/" new-id))))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-name collection-acl resource-uri))

(defmethod crud/add resource-name
  [{{:keys [method]} :body :as request}]
  (if (get @descriptions method)
    (add-impl request)
    (throw (r/ex-bad-request (str "invalid authentication method '" method "'")))))

(def retrieve-impl (std-crud/retrieve-fn resource-name))

(defmethod crud/retrieve resource-name
  [request]
  (retrieve-impl request))

(def edit-impl (std-crud/edit-fn resource-name))

(defmethod crud/edit resource-name
  [request]
  (edit-impl request))

(def delete-impl (std-crud/delete-fn resource-name))

(defmethod crud/delete resource-name
  [request]
  (delete-impl request))

(def query-impl (std-crud/query-fn resource-name collection-acl collection-uri resource-tag))

(defmethod crud/query resource-name
  [request]
  (query-impl request))

;;
;; override the operations method to add describe action
;;

(defmethod crud/set-operations resource-uri
  [{:keys [id resourceURI] :as resource} request]
  (let [href (str id "/describe")]
    (try
      (a/can-modify? resource request)
      (let [ops (if (.endsWith resourceURI "Collection")
                  [{:rel (:add c/action-uri) :href id}]
                  [{:rel (:edit c/action-uri) :href id}
                   {:rel (:delete c/action-uri) :href id}
                   {:rel (:describe c/action-uri) :href href}])]
        (assoc resource :operations ops))
      (catch Exception e
        (if (.endsWith resourceURI "Collection")
          (dissoc resource :operations)
          (assoc resource :operations [{:rel (:describe c/action-uri) :href href}]))))))

;;
;; actions
;;

(defmethod crud/do-action [resource-url "describe"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-url "/" uuid)]
      (if-let [{:keys [method] :as resource} (crud/retrieve-by-id-as-admin id)]
        (if (a/can-view? resource request)
          (if-let [desc (get @descriptions method)]
            (r/json-response desc)
            (r/ex-not-found id)))
        (r/ex-not-found id)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; initialization: create metadata for this collection
;;
(defn initialize
  []
  (md/register (gen-md/generate-metadata ::ns ::session-tpl/schema)))

