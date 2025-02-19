(ns com.sixsq.nuvla.server.resources.nuvlabox-status
  "
The `nuvlabox-status` resource represents the instantaneous state of the
associated NuvlaBox. These resources are usually created as a side-effect of a
NuvlaBox activation, although they can be created manually by an administrator.
Versioned subclasses define the attributes for a particular NuvlaBox release.
"
  (:require
    [clojure.spec.alpha :as s]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.nuvlabox.data-utils :as data-utils]
    [com.sixsq.nuvla.server.resources.nuvlabox.status-utils :as utils]
    [com.sixsq.nuvla.server.resources.nuvlabox.utils :as nb-utils]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.nuvlabox-status :as nb-status]
    [com.sixsq.nuvla.server.util.kafka-crud :as kafka-crud]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]
    [com.sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:add   ["group/nuvla-admin"]
                     :query ["group/nuvla-user"]})


;;
;; multimethods for validation
;;

(defmulti validate-subtype
          "Validates the given nuvlabox-status resource against a specific
           version of the schema."
          :version)


(defmethod validate-subtype :default
  [{:keys [version] :as _resource}]
  (if version
    (throw (r/ex-bad-request (str "unsupported nuvlabox-status version: " version)))
    (throw (r/ex-bad-request "missing nuvlabox-status version"))))

(defmulti spec-subtype :version)


(defmethod crud/validate resource-type
  [resource]
  (validate-subtype resource))

(defn heuristic-spec-problem-fixer
  [resource {:keys [pred val in] :as _problem}]
  (if (and (set? pred)
           (keyword? val)
           (= (last in) 0))
    (if-let [path (seq (drop-last 2 in))]
      (update-in resource path dissoc val)
      (dissoc resource val))
    (reduced resource)))

(defn fix-resource-heuristic
  [resource]
  (let [problems (::s/problems (s/explain-data (spec-subtype resource) resource))]
    (reduce heuristic-spec-problem-fixer resource problems)))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [request]
  (add-impl request))


(def blacklist-response-keys [:resources-prev
                              :online-prev])


(defn remove-blacklisted
  [response]
  (if (= collection-type (get-in response [:body :resource-type]))
    (update-in response [:body :resources]
               #(for [r %] (apply dissoc r blacklist-response-keys)))
    (update response :body #(apply dissoc % blacklist-response-keys))))

(defn create-nuvlabox-status
  "Utility to facilitate creating a new nuvlabox-status resource from the
   nuvlabox resource. This will create (as an administrator) an unknown
   state based on the given id and acl. The returned value is the standard
   'add' response for the request."
  [{:keys [id version] :as nuvlabox}]
  (let [body    (utils/set-name-description-acl
                  {:resource-type resource-type
                   :parent        id
                   :version       version
                   :status        "UNKNOWN"}
                  nuvlabox)
        request {:params      {:resource-name resource-type}
                 :nuvla/authn auth/internal-identity
                 :body        body}]
    (add-impl request)))


(defn pre-delete-attrs-hook
  [{nb-id          :parent
    resources-prev :resources :as resource}
   request]
  (let [nb             (crud/retrieve-by-id-as-admin nb-id)
        ne-deployments (utils/get-ne-deployments resource)]
    (-> (nb-utils/throw-parent-nuvlabox-is-suspended resource nb)
        (nb-utils/legacy-heartbeat request nb)
        (utils/status-telemetry-attributes nb)
        (utils/set-name-description-acl nb)
        (utils/update-deployment-parameters ne-deployments)
        (utils/create-deployment-state-jobs ne-deployments)
        (cond-> (some? resources-prev)
                (assoc :resources-prev resources-prev)))))

(defn post-edit
  [response request]
  (utils/denormalize-changes-nuvlabox (r/response-body response))
  (utils/detect-swarm response)
  (kafka-crud/publish-on-edit resource-type response)
  (data-utils/bulk-insert-metrics (:body response) true)
  (data-utils/track-availability (:body response) true)
  (utils/special-body-nuvlabox response request))

(defn get-invalid-resource-ex
  [resource]
  (try
    (crud/validate resource)
    nil
    (catch Exception ex
      ex)))

(defn pre-validate-hook
  [resource request]
  (if-let [exception (get-invalid-resource-ex resource)]
    (let [fix-resource-attempt (fix-resource-heuristic resource)]
      (if (nil? (get-invalid-resource-ex fix-resource-attempt))
        (do
          (log/error "Some Nuvlabox Status fields where ignored because of spec issues!: " exception)
          fix-resource-attempt)
        (do (-> request
                (assoc-in [:headers "content-type"] "application/json")
                (assoc :body nil)
                crud/edit)
            (throw exception))))
    resource))

(def edit-impl (std-crud/edit-fn resource-type
                                 :pre-delete-attrs-hook pre-delete-attrs-hook
                                 :pre-validate-hook pre-validate-hook
                                 :immutable-keys [:online
                                                  :online-prev
                                                  :last-heartbeat
                                                  :next-heartbeat
                                                  :last-telemetry
                                                  :next-telemetry]
                                 :options {:refresh false}))


(defmethod crud/edit resource-type
  [request]
  (try
    (let [response (edit-impl request)]
      (if (r/status-200? response)
        (-> response
            (post-edit request)
            remove-blacklisted)
        response))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defn update-nuvlabox-status
  [status-id nuvlabox]
  (crud/edit {:params      {:uuid          (u/id->uuid status-id)
                            :resource-name resource-type}
              :body        (utils/set-name-description-acl {} nuvlabox)
              :nuvla/authn auth/internal-identity}))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (-> (retrieve-impl request)
      remove-blacklisted))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (-> (query-impl request)
      remove-blacklisted))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::nb-status/schema))

(defn initialize
  []
  (std-crud/initialize resource-type ::nb-status/schema)
  (md/register resource-metadata))
