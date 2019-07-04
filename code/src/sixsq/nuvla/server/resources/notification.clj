(ns sixsq.nuvla.server.resources.notification
  "
The `notification` resource allows creation and deletion of notification
messages.

Each `notification` should be assigned a category. There are no predefined
categories.

Each `notification` resource must have `content-unique-id` set. This field
should be used by the publisher to uniquely label the notification based on
some of its fields (e.g.: be a hash of `message`, `category` and
`target-resource` fields). This field should allow a `notification` to be
uniquely identified and thus, simplify searching.

Notifications can have `target-resource` field set to identify the resource
for which the notification was published.

A `notification` can have `not-before` field set via the `defer` action to
signal a notification delivery mechanism to hide this notification until the
defined time. Deferring notification can be done any number of times.

On `notification` creation, an optional `callback` field can be set for
binding it to an existing callback. An external `notification` handler is
responsible for calling the callback, if and when required.

Notifications can not be edited.

**Notifications can only be created by admins and by default, will only be
visible to admins.** Consequently, the creator of the `notification` should
provide an explicit ACL that the describes the desired visibility.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.notification :as notification]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.server.util.time :as t]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-admin"]})

(def resource-acl {:owners ["group/nuvla-admin"]})


(def ^:const defer-param-name "minutes")
(def ^:const defer-param-kw (keyword defer-param-name))
(def ^:const delay-default 30)
(def actions [{:name             "defer"
               :uri              "defer"
               :description      "defer the notification for the number of minutes"
               :method           "POST"
               :input-message    "application/json"
               :output-message   "application/json"
               :input-parameters [{:name        defer-param-name
                                   :value-scope {:minimum 1
                                                 :units   "minutes"
                                                 :default delay-default}}]}])


;;
;; "Implementations" of multimethod declared in crud namespace
;;

(def validate-fn (u/create-spec-validation-fn ::notification/schema))


(defmethod crud/validate
  resource-type
  [resource]
  (validate-fn resource))


(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [request]
  (add-impl request))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


;;
;; use default ACL method
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (if (u/is-collection? resource-type)
    (assoc resource :acl collection-acl)
    (assoc resource :acl (merge resource-acl (-> request :body :acl)))))


;;
;; available collection and resource operations
;;


(defn set-collection-ops
  [{:keys [id] :as resource} request]
  (if (a/can-add? resource request)
    (let [ops [(u/operation-map id :add)]]
      (assoc resource :operations ops))
    (dissoc resource :operations)))


(defn set-resource-ops
  [{:keys [id] :as resource} request]
  (let [can-manage? (a/can-manage? resource request)
        ops         (cond-> []
                            can-manage? (conj (u/operation-map id :delete))
                            can-manage? (conj (u/action-map id :defer)))]
    (if (seq ops)
      (assoc resource :operations ops)
      (dissoc resource :operations))))


(defmethod crud/set-operations resource-type
  [resource request]
  (if (u/is-collection? resource-type)
    (set-collection-ops resource request)
    (set-resource-ops resource request)))


(defn- throw-not-pos-int?
  [n]
  (if-not (pos-int? n)
    (throw (r/ex-bad-request "delay should be a positive integer.")))
  n)


(defn delay->from-now
  [minutes]
  (-> minutes
      throw-not-pos-int?
      (t/from-now :minutes)
      t/to-str))


(defmethod crud/do-action [resource-type "defer"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id         (str resource-type "/" uuid)
          not-before (delay->from-now
                       (or (-> request :body defer-param-kw) delay-default))]
      (-> id
          (db/retrieve request)
          (a/throw-cannot-manage request)
          (assoc :not-before not-before)
          (crud/validate)
          (db/edit request))
      (r/map-response (str id " deferred until " not-before) 200 id))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; collection
;;

(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [{{:keys [orderby]} :cimi-params :as request}]
  (query-impl (assoc-in request [:cimi-params :orderby] (if (seq orderby) orderby [["updated" :desc]]))))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize resource-type ::notification/schema)
  (md/register (gen-md/generate-metadata ::ns ::notification/schema)))
