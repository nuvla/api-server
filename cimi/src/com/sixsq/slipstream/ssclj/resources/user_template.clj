(ns com.sixsq.slipstream.ssclj.resources.user-template
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
curl 'https://nuv.la/api/user-template?$select=name,description'
```

```json
{
  \"count\" : 15,
  ...
  \"resourceURI\" : \"http://sixsq.com/slipstream/1/UserTemplateCollection\",
  \"id\" : \"user-template\",
  \"userTemplates\" : [ {
    \"name\" : \"ESRF Realm\",
    \"description\" : \"Creates a new user through OIDC registration\",
    ...
    },
    \"resourceURI\" : \"http://sixsq.com/slipstream/1/UserTemplate\",
    \"operations\" : [ {
      \"rel\" : \"http://sixsq.com/slipstream/1/action/describe\",
      \"href\" : \"/describe\"
    } ]
  }, {
    \"name\" : \"INFN Realm\",
    \"description\" : \"Creates a new user through OIDC registration\",
    ...

```
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
    [com.sixsq.slipstream.ssclj.resources.spec.user-template :as user-tpl]
    [com.sixsq.slipstream.ssclj.util.metadata :as gen-md]
    [com.sixsq.slipstream.util.response :as r]))

(def ^:const resource-tag :userTemplates)

(def ^:const resource-name "UserTemplate")

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "UserTemplateCollection")

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
;; atom to keep track of the UserTemplate descriptions
;;

(def descriptions (atom {}))


(defn register
  "Registers a given UserTemplate description with the server. The description
   (desc) must be valid. The method attribute is used as the key to look up the
   correct description."
  [method desc]
  (when (and method desc)
    (let [full-desc (assoc desc :acl desc-acl)]
      (swap! descriptions assoc method full-desc)
      (log/info "loaded UserTemplate description" method))))


;;
;; schemas
;;

(def UserTemplateDescription
  (merge c/CommonParameterDescription
         hints/UIHintsParameterDescription
         {:method   {:displayName "Registration Method"
                     :category    "general"
                     :description "method to be used to register user"
                     :type        "string"
                     :mandatory   true
                     :readOnly    true
                     :order       10}
          :instance {:displayName "Registration Instance"
                     :category    "general"
                     :description "registration instance identifier"
                     :type        "string"
                     :mandatory   true
                     :readOnly    true
                     :order       11}}))

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

(defmethod crud/new-identifier resource-name
  [{:keys [instance] :as resource} resource-name]
  (->> instance
       (str resource-url "/")
       (assoc resource :id)))

;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-name collection-acl resource-uri))

(defmethod crud/add resource-name
  [{{:keys [method]} :body :as request}]
  (if (get @descriptions method)
    (add-impl request)
    (throw (r/ex-bad-request (str "invalid registration method '" method "'")))))


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
  (md/register (gen-md/generate-metadata ::ns ::user-tpl/schema)))

