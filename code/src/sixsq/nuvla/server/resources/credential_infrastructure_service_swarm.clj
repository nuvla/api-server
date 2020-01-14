(ns sixsq.nuvla.server.resources.credential-infrastructure-service-swarm
  "
This resource contains the values necessary to access a Docker Swarm service.
These consist of a public 'cert' and the associated private 'key'. The
certificate authority's public certificate, 'ca', should also be provided.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-swarm :as tpl]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-swarm
     :as service-swarm]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::service-swarm/schema))


(defn initialize
  []
  (std-crud/initialize p/resource-type ::service-swarm/schema)
  (md/register resource-metadata))


;;
;; convert template to credential: just copies the necessary keys from the provided template.
;;

(defmethod p/tpl->credential tpl/credential-subtype
  [{:keys [subtype method parent ca cert key acl]} request]
  [nil (cond-> {:resource-type p/resource-type
                :subtype       subtype
                :method        method
                :ca            ca
                :cert          cert
                :key           key}
               acl (assoc :acl acl)
               parent (assoc :parent parent))])


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::service-swarm/schema))


(defmethod p/validate-subtype tpl/credential-subtype
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::service-swarm/schema-create))


(defmethod p/create-validate-subtype tpl/credential-subtype
  [resource]
  (create-validate-fn resource))


;;
;; operations
;;


(defn set-resource-ops
  [{:keys [id] :as resource} request]
  (let [can-manage? (a/can-manage? resource request)
        ops         (cond-> []
                            (a/can-edit? resource request) (conj (u/operation-map id :edit))
                            (a/can-delete? resource request) (conj (u/operation-map id :delete))
                            can-manage? (conj (u/action-map id :check-coe)))]
    (if (seq ops)
      (assoc resource :operations ops)
      (dissoc resource :operations))))


(defmethod p/set-credential-operations tpl/credential-subtype
  [{:keys [resource-type] :as resource} request]
  (if (u/is-collection? resource-type)
    (crud/set-standard-collection-operations resource request)
    (set-resource-ops resource request)))


;;
;; actions
;;

(defn create-job
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str p/resource-type "/" uuid)]
      (if-let [user-id (auth/current-user-id request)]
        (let [job-type "check_coe"
              {{job-id     :resource-id
                job-status :status} :body} (job/create-job id job-type
                                                           {:owners   ["group/nuvla-admin"]
                                                            :view-acl [user-id]}
                                                           :priority 50)
              job-msg  (str "starting " id " with async " job-id)]
          (when (not= job-status 201)
            (throw (r/ex-response (format "unable to create async job to % log" job-type) 500 id)))
          (r/map-response job-msg 202 id job-id))
        (throw (r/ex-response "current authentication has no session identifier" 500 id))))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [p/resource-type "check-coe"]
  [{{uuid :uuid} :params :as request}]
  (let [id       (str p/resource-type "/" uuid)
        resource (crud/retrieve-by-id-as-admin id)]
    (a/throw-cannot-manage resource request)
    (create-job request)))

