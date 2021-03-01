(ns sixsq.nuvla.server.resources.spec.nuvlabox-status-1
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.nuvlabox-status :as nb-status]
    [sixsq.nuvla.server.resources.spec.nuvlabox-status-0 :as nb-status-0]
    [sixsq.nuvla.server.util.spec :as su]))


;; This version of the schema is the same as the previous one (0),
;; except that the peripherals attribute has been removed. Use the
;; same attribute definitions to avoid repetition.


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     nb-status/attributes
                     {:req-un [::nb-status-0/status]
                      :opt-un [::nb-status-0/next-heartbeat
                               ::nb-status-0/current-time
                               ::nb-status-0/comment
                               ::nb-status-0/resources
                               ::nb-status-0/resources-prev
                               ::nb-status-0/operating-system
                               ::nb-status-0/architecture
                               ::nb-status-0/hostname
                               ::nb-status-0/ip
                               ::nb-status-0/docker-server-version
                               ::nb-status-0/last-boot
                               ::nb-status-0/wifi-password
                               ::nb-status-0/nuvlabox-api-endpoint
                               ::nb-status-0/inferred-location
                               ::nb-status-0/gpio-pins
                               ::nb-status-0/nuvlabox-engine-version
                               ::nb-status-0/gpio-pins
                               ::nb-status-0/docker-plugins
                               ::nb-status-0/vulnerabilities
                               ::nb-status-0/swarm-node-id
                               ::nb-status-0/installation-parameters
                               ::nb-status-0/jobs
                               ::nb-status-0/swarm-node-cert-expiry-date
                               ::nb-status-0/online
                               ::nb-status-0/online-prev]}))

