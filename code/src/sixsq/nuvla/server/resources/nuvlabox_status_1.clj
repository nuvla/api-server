(ns sixsq.nuvla.server.resources.nuvlabox-status-1
  "
The nuvlabox-status (version 1) contains attributes to describe available
resources.
"
  (:require
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.nuvlabox-status :as nb-status]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.nuvlabox-status-1 :as nb-status-1]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.time :as time]))


(def schema-version 1)


;;
;; multimethod for validation
;;

(def validate-fn (u/create-spec-validation-fn ::nb-status-1/schema))


(defmethod nb-status/validate-subtype schema-version
  [resource]
  (validate-fn resource))


;;
;; multimethod to modify an edit request
;;

(defn get-next-heartbeat
  [nuvlabox-id]
  (try
    (some-> nuvlabox-id
            crud/retrieve-by-id-as-admin
            :refresh-interval
            (time/from-now :seconds)
            time/to-str)
    (catch Exception _
      nil)))

(defmethod nb-status/pre-edit schema-version
  [{:keys [parent] :as resource}]

  ;; overwrites the next-heartbeat time based on the refresh-interval
  (let [next-heartbeat (get-next-heartbeat parent)]
    (cond-> resource
            next-heartbeat (assoc :next-heartbeat next-heartbeat))))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::nb-status/ns ::nb-status-1/schema))


(defn initialize
  []
  (std-crud/initialize nb-status/resource-type ::nb-status-1/schema)
  (md/register resource-metadata))
