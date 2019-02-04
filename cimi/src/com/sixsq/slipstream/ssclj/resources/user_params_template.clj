(ns com.sixsq.slipstream.ssclj.resources.user-params-template
  "
The UserParamTemplate resources allow UserParam resources to be created.
Normally, users and administrators will never create a UserParam resource
manually and will not need to use these templates.
"
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.slipstream.auth.acl :as a]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.util.response :as r]))

(def ^:const resource-tag :userParamTemplates)

(def ^:const resource-name "UserParamTemplate")

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "UserParamTemplateCollection")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def ^:const collection-uri (str c/slipstream-schema-uri collection-name))

(def resource-acl {:owner {:principal "ADMIN"
                           :type      "ROLE"}
                   :rules [{:principal "USER"
                            :type      "ROLE"
                            :right     "VIEW"}]})

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "USER"
                              :type      "ROLE"
                              :right     "VIEW"}]})

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
      (assoc skeleton collection-key entries))))

(defn complete-resource
  "Completes the given document with server-managed information:
   resourceURI, timestamps, operations, and ACL."
  [{:keys [paramsType] :as resource}]
  (let [id  (str resource-url "/" paramsType)
        ops [{:rel  (:describe c/action-uri)
              :href (str id "/describe")}]]
    (-> resource
        (merge {:id          id
                :resourceURI resource-uri
                :acl         resource-acl
                :operations  ops})
        u/update-timestamps)))

(defn register
  "Registers UserExecParametersTemplate resource and its description
   with the server.  The resource document (resource) and the description
   (desc) must be valid.  The key will be used to create the id of
   the resource as 'user-exec-params-template/key'."
  [resource desc]
  (when-let [full-resource (complete-resource resource)]
    (let [id (:id full-resource)]
      (swap! templates assoc id full-resource)
      (log/info "loaded UserExecParamsTemplate" id)
      (when desc
        (let [acl       (:acl full-resource)
              full-desc (assoc desc :acl acl)]
          (swap! descriptions assoc id full-desc))
        (log/info "loaded UserExecParamsTemplate description" id)))))

;;
;; schemas
;;

(def UserParamTemplateDescription
  (merge c/CommonParameterDescription
         {:paramsType {:displayName "Type of the set of the parameters."
                       :category    "general"
                       :description "Grouping type of the parameters."
                       :type        "string"
                       :mandatory   true
                       :readOnly    true
                       :order       10}}))

;;
;; multimethods for validation
;;

(defmulti validate-subtype
          "Validates the given resource against the specific
           UserParamTemplate subtype schema."
          :paramsType)

(defmethod validate-subtype :default
  [resource]
  (throw (ex-info (str "unknown UserParamTemplate type: " (:paramsType resource))
                  resource)))

(defmethod crud/validate resource-uri
  [resource]
  (validate-subtype resource))

;; must override the default implementation so that the
;; data can be pulled from the atom rather than the database
(defmethod crud/retrieve resource-name
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-url "/" uuid)]
      (-> (get @templates id)
          (a/can-view? request)
          (r/json-response)))
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defmethod crud/retrieve-by-id resource-url
  [id]
  (try
    (get @templates id)
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defmethod crud/add resource-name
  [request]
  (throw (r/ex-bad-method request)))

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
  (let [wrapper-fn              (collection-wrapper-fn resource-name collection-acl collection-uri resource-tag)
        entries                 (or (filter (partial viewable? request) (vals @templates)) [])
        ;; FIXME: At least the paging options should be supported.
        options                 (select-keys request [:identity :query-params :cimi-params :user-name :user-roles])
        count-before-pagination (count entries)
        wrapped-entries         (wrapper-fn request entries)
        entries-and-count       (assoc wrapped-entries :count count-before-pagination)]
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
