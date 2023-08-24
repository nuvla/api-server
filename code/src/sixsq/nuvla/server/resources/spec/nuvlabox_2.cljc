(ns sixsq.nuvla.server.resources.spec.nuvlabox-2
  (:require
    [clojure.spec.alpha :as s]
    [spec-tools.core :as st]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.nuvlabox :as nb]
    [sixsq.nuvla.server.resources.spec.nuvlabox-0 :as nb-0]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]))


;; This version of the schema is the same as the previous one (0).
;; Use the same attribute definitions to avoid repetition.

(s/def ::heartbeat-interval
  (assoc (st/spec pos-int?)
    :name "hearthbeat-interval"
    :json-schema/display-name "hearthbeat interval"
    :json-schema/description "hearthbeat interval in seconds"

    :json-schema/order 38))

(s/def ::next-heartbeat
  (assoc (st/spec ::core/timestamp)
    :name "next-heartbeat"
    :json-schema/display-name "next heartbeat"
    :json-schema/description "timestamp for next heartbeat update"

    :json-schema/order 39))

(s/def ::last-heartbeat
  (assoc (st/spec ::core/timestamp)
    :name "last-heartbeat"
    :json-schema/display-name "last heartbeat"
    :json-schema/description "timestamp for last heartbeat update"

    :json-schema/order 40))

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
                               ::nb-0/comment
                               ::nb-0/lan-cidr
                               ::nb-0/hw-revision-code
                               ::nb-0/monitored
                               ::nb-0/vpn-server-id
                               ::nb-0/internal-data-gateway-endpoint
                               ::nb-0/ssh-keys
                               ::nb-0/capabilities
                               ::nb-0/online
                               ::nb-0/inferred-location
                               ::nb-0/nuvlabox-engine-version
                               ::heartbeat-interval
                               ::last-heartbeat
                               ::next-heartbeat]}))
