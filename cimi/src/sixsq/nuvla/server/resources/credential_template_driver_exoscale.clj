(ns sixsq.nuvla.server.resources.credential-template-driver-exoscale
  (:require
    [sixsq.nuvla.server.resources.credential-template :as p]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.resources.spec.credential-template-driver-exoscale :as driver]))


(def ^:const resource-acl-default {:owner {:principal "ADMIN"
                                           :type      "ROLE"}
                                   :rules [{:principal "USER"
                                            :type      "ROLE"
                                            :right     "VIEW"}]})


(def ^:const resource-base
  {:name        "User cloud credentials store"
   :description "Stores user cloud credentials"
   :exoscale-api-key         ""
   :exoscale-api-secret-key  ""
   :acl         resource-acl-default})



(def ^:const cred-type "cloud-driver-cred-exoscale")
(def ^:const cred-method "store-cloud-driver-cred-exoscale")

(def ^:const resource-name "Exoscale driver API keys")

(def ^:const resource-url cred-type)



;(defn cred-type
;  [cloud-service-type]
;  (str "cloud-driver-cred-" cloud-service-type))
;
;
;(defn cred-method
;  [cloud-service-type]
;  (str "store-cloud-driver-cred-" cloud-service-type))


(defn gen-resource
  [cred-instance-map cloud-service-type]
  (merge resource-base
         {:name        (str "User cloud credentials store for driver " cloud-service-type)
          :description (str "Stores user cloud credentials for cloud driver " cloud-service-type)
          :type        cred-type
          :method      cred-method}
         cred-instance-map))


(defn register
  [cred-instance-map cloud-service-type]
  (p/register (gen-resource cred-instance-map cloud-service-type)))



;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::driver/schema))
(defmethod p/validate-subtype cred-method
           [resource]
           (validate-fn resource))


;;
;; initialization: register this Credential template
;;

(defn initialize
      []
      (p/register resource-base)
      (md/register (gen-md/generate-metadata ::ns ::p/ns ::driver/schema)))