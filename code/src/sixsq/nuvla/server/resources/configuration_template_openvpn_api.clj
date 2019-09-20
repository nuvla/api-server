(ns sixsq.nuvla.server.resources.configuration-template-openvpn-api
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-template :as p]
    [sixsq.nuvla.server.resources.spec.configuration-template-openvpn-api :as cts-openvpn-api]))


(def ^:const service "openvpn-api")


;;
;; resource
;;

(def ^:const resource
  {:service     service
   :name        "OpenVPN API Configuration"
   :description "OpenVPN API Configuration to use when creating openvpn credentials"
   :instance    "openvpn-instance"
   :endpoint    "http://openvpn.example"})


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::cts-openvpn-api/schema))


(defmethod p/validate-subtype service
  [resource]
  (validate-fn resource))


;;
;; initialization: register this Configuration template
;;

(defn initialize
  []
  (p/register resource))
