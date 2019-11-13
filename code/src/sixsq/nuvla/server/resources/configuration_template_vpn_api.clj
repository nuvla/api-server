(ns sixsq.nuvla.server.resources.configuration-template-vpn-api
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-template :as p]
    [sixsq.nuvla.server.resources.spec.configuration-template-vpn-api :as cts-vpn-api]))


(def ^:const service "vpn-api")


;;
;; resource
;;

(def ^:const resource
  {:service                 service
   :name                    "VPN API Configuration"
   :description             "VPN API Configuration to use when creating vpn credentials"
   :instance                "vpn-instance"
   :endpoint                "http://vpn.example"
   :infrastructure-services ["infrastructure-service/vpn-example"]})


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::cts-vpn-api/schema))


(defmethod p/validate-subtype service
  [resource]
  (validate-fn resource))


;;
;; initialization: register this Configuration template
;;

(defn initialize
  []
  (p/register resource))
