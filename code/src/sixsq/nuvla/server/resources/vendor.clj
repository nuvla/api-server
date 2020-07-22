(ns sixsq.nuvla.server.resources.vendor
  "
Application vendor resources allow users to sell their apps within Nuvla
marketplace.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.server.resources.spec.vendor :as vendor]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.util.response :as r]
    [ring.util.codec :as codec]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


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


(defn create-callback
  [base-uri state active-claim redirect-url]
  (let [callback-request {:params      {:resource-name callback/resource-type}
                          :body        {:action "vendor-creation"
                                        :data   (cond-> {:state        state
                                                         :active-claim active-claim}
                                                        redirect-url (assoc :redirect-url
                                                                            redirect-url))}
                          :nuvla/authn auth/internal-identity}
        {{:keys [resource-id]} :body status :status} (crud/add callback-request)]
    (if (= 201 status)
      (if-let [callback-resource (crud/set-operations (crud/retrieve-by-id-as-admin resource-id) {})]
        (if-let [validate-op (u/get-op callback-resource "execute")]
          (str base-uri validate-op)
          (let [msg "callback does not have execute operation"]
            (throw (ex-info msg (r/map-response msg 500 resource-id)))))
        (let [msg "cannot retrieve  session callback"]
          (throw (ex-info msg (r/map-response msg 500 resource-id)))))
      (let [msg "cannot create  session callback"]
        (throw (ex-info msg (r/map-response msg 500)))))))

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defmethod crud/add resource-type
  [{:keys [body base-uri] :as request}]
  (config-nuvla/throw-stripe-not-configured)
  (a/throw-cannot-add collection-acl request)
  (try
    (let [state                (u/random-uuid)
          redirect-url         (:redirect-url body)
          active-claim         (auth/current-active-claim request)
          execute-callback-url (create-callback base-uri state active-claim redirect-url)
          oauth-url            (str "https://connect.stripe.com/express/oauth/authorize?"
                                    "redirect_uri=" (codec/url-encode execute-callback-url)
                                    "&client_id=" (codec/url-encode "") ;;FIXME
                                    "&state=" (codec/url-encode state)
                                    "&response_type=" (codec/url-encode "code"))]
      {:status 303, :headers {"Location" oauth-url}})
    ;; create callback and save in data redirect-url when provided and generate secret as state
    ;; create oauth link with state and redirect param
    ;; redirect user to oauth link
    (catch Exception e
      (or (ex-data e)
          (throw e)))))


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

;;
;; initialization: common schema for all user creation methods
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::vendor/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::vendor/schema)
  (md/register resource-metadata))



