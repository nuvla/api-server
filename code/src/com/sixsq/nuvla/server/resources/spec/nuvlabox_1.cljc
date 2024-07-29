(ns com.sixsq.nuvla.server.resources.spec.nuvlabox-1
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.nuvlabox :as nb]
    [com.sixsq.nuvla.server.resources.spec.nuvlabox-0 :as nb-0]
    [com.sixsq.nuvla.server.util.spec :as su]))


;; This version of the schema is the same as the previous one (0).
;; Use the same attribute definitions to avoid repetition.

(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     nb/attributes
                     {:req-un [::nb-0/state
                               ::nb-0/refresh-interval]
                      :opt-un [::nb-0/location
                               ::nb-0/supplier
                               ::nb-0/organization
                               ::nb-0/manufacturer-serial-number
                               ::nb-0/firmware-version
                               ::nb-0/hardware-type
                               ::nb-0/form-factor
                               ::nb-0/wifi-ssid
                               ::nb-0/wifi-password
                               ::nb-0/root-password
                               ::nb-0/login-username
                               ::nb-0/login-password
                               ::nb-0/cloud-password
                               ::nb-0/comment
                               ::nb-0/vm-cidr
                               ::nb-0/lan-cidr
                               ::nb-0/os-version
                               ::nb-0/hw-revision-code
                               ::nb-0/monitored
                               ::nb-0/vpn-server-id
                               ::nb-0/internal-data-gateway-endpoint
                               ::nb-0/ssh-keys
                               ::nb-0/capabilities
                               ::nb-0/online
                               ::nb-0/inferred-location
                               ::nb-0/nuvlabox-engine-version
                               ::nb-0/heartbeat-interval]}))
