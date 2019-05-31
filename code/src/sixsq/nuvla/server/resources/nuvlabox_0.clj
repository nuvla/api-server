(ns sixsq.nuvla.server.resources.nuvlabox-0
  "
The nuvlabox (version 0) contains attributes to describe and configure
a NuvlaBox.
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.nuvlabox :as nb]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as nb-utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.nuvlabox-0 :as nb-0]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))


(def schema-version 0)


;;
;; multimethod for validation
;;

(def validate-fn (u/create-spec-validation-fn ::nb-0/schema))


(defmethod nb/validate-subtype schema-version
  [resource]
  (validate-fn resource))


;;
;; multimethod for commission
;;

(defmethod nb/commission schema-version
  [{:keys [id owner] :as resource}
   {{:keys [swarm-endpoint
            swarm-token-manager swarm-token-worker
            swarm-client-key swarm-client-cert swarm-client-ca
            minio-endpoint
            minio-access-key minio-secret-key]} :body :as request}]

  (when-let [isg-id (nb-utils/get-isg-id id)]
    (let [swarm-id (nb-utils/create-swarm-service id owner isg-id swarm-endpoint)
          minio-id (nb-utils/create-minio-service id owner isg-id minio-endpoint)]

      (when swarm-id
        (nb-utils/create-swarm-cred id owner swarm-id swarm-client-key swarm-client-cert swarm-client-ca)
        (nb-utils/create-swarm-token id owner swarm-id "MANAGER" swarm-token-manager)
        (nb-utils/create-swarm-token id owner swarm-id "WORKER" swarm-token-worker))

      (when minio-id
        (nb-utils/create-minio-cred id owner minio-id minio-access-key minio-secret-key)))))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize nb/resource-type ::nb-0/schema)
  (md/register (gen-md/generate-metadata ::ns ::nb/ns ::nb-0/schema schema-version)))
