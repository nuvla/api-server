(ns sixsq.nuvla.server.resources.vendor
  "
Application vendor resources allow users to sell their apps within Nuvla
marketplace.
"
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.vendor :as vendor]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]
    [clojure.walk :as walk]
    [sixsq.nuvla.server.resources.pricing.stripe :as stripe]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})


;;
;; common validation for created resource
;;

(def validate-fn (u/create-spec-validation-fn ::vendor/schema))

(defmethod crud/validate
  resource-type
  [resource]
  (validate-fn resource))


;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))

;;
;; CRUD operations
;;


(defn active-claim->resource-id
  [active-claim]
  (->> active-claim
       u/parse-id
       (str/join "-")
       (str resource-type "/")))

(defn request->resource-id
  [{{uuid :uuid} :params :as request}]
  (str resource-type "/" uuid))


;; resource identifier a UUID generated from the user-id
(defmethod crud/new-identifier resource-type
  [resource resource-name]
  (assoc resource :id (-> resource :parent active-claim->resource-id)))


(defn create-callback
  [active-claim redirect-url]
  (let [callback-request {:params      {:resource-name callback/resource-type}
                          :body        {:action "vendor-creation"
                                        :data   (cond-> {:active-claim active-claim}
                                                        redirect-url (assoc :redirect-url
                                                                            redirect-url))}
                          :nuvla/authn auth/internal-identity}
        {{:keys [resource-id]} :body status :status} (crud/add callback-request)]
    (if (= 201 status)
      (if-let [callback-resource (crud/set-operations (crud/retrieve-by-id-as-admin resource-id) {})]
        (if-let [validate-op (u/get-op callback-resource "execute")]
          validate-op
          (let [msg "callback does not have execute operation"]
            (throw (ex-info msg (r/map-response msg 500 resource-id)))))
        (let [msg "cannot retrieve  session callback"]
          (throw (ex-info msg (r/map-response msg 500 resource-id)))))
      (let [msg "cannot create  session callback"]
        (throw (ex-info msg (r/map-response msg 500)))))))


(defn create-redirect-url
  "Generate a redirect-url from the provided authorizeURL"
  [client-id callback-url]
  (let [url-params-format "?response_type=code&client_id=%s&state=%s"]
    (str "https://connect.stripe.com/express/oauth/authorize"
         (format url-params-format
                 client-id
                 callback-url))))


(defn throw-account-exist
  [id]
  (let [account-id (try
                     (-> id
                         crud/retrieve-by-id-as-admin
                         :account-id)
                     (catch Exception _))]
    (when account-id
      (logu/log-and-throw-400 "Vendor already exist!"))))

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [{:keys [headers body form-params] :as request}]
  (config-nuvla/throw-stripe-not-configured)
  (a/throw-cannot-add collection-acl request)
  (let [active-claim (auth/current-active-claim request)]
    (throw-account-exist (active-claim->resource-id active-claim))
    (try
      (let [body         (if (u/is-form? headers) (walk/keywordize-keys form-params) body)
            redirect-url (:redirect-url body)
            callback-url (create-callback active-claim redirect-url)
            oauth-url    (create-redirect-url config-nuvla/*stripe-client-id* callback-url)]
        {:status 303, :headers {"Location" oauth-url}})
      (catch Exception e
        (or (ex-data e)
            (throw e))))))


(def retrieve-impl (std-crud/retrieve-fn resource-type))

(defmethod crud/retrieve resource-type
  [request]
  (config-nuvla/throw-stripe-not-configured)
  (retrieve-impl request))


(def delete-impl (std-crud/delete-fn resource-type))

(defmethod crud/delete resource-type
  [request]
  (config-nuvla/throw-stripe-not-configured)
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))

(defmethod crud/query resource-type
  [request]
  (config-nuvla/throw-stripe-not-configured)
  (query-impl request))


(def ^:const dashboard-action "dashboard")

(defmethod crud/set-operations resource-type
  [{:keys [id] :as resource} request]
  (let [can-manage?  (a/can-manage? resource request)
        dashboard-op (u/action-map id dashboard-action)]
    (cond-> (crud/set-standard-operations resource request)
            can-manage? (update :operations conj dashboard-op))))


(defn account-id->dashboard-url
  [account-id]
  (some-> account-id
          stripe/login-link-create-on-account
          stripe/get-url))


(defmethod crud/do-action [resource-type dashboard-action]
  [request]
  (config-nuvla/throw-stripe-not-configured)
  (try
    (let [dashboard-url (some-> request
                                (request->resource-id)
                                (crud/retrieve-by-id-as-admin)
                                (a/throw-cannot-manage request)
                                :account-id
                                account-id->dashboard-url)]
      {:status 303, :headers {"Location" dashboard-url}})
    (catch Exception e
      (or (ex-data e) (throw e)))))

;;
;; initialization: common schema for all user creation methods
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::vendor/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::vendor/schema)
  (md/register resource-metadata))



