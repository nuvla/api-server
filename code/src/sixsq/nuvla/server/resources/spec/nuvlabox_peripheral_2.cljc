(ns sixsq.nuvla.server.resources.spec.nuvlabox-peripheral-2
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.nuvlabox-peripheral-1 :as nb-p-1]
    [sixsq.nuvla.server.resources.spec.nuvlabox-status :as nb-status]
    [sixsq.nuvla.server.util.spec :as su]))

(s/def ::schema
  (su/only-keys-maps common/common-attrs
    nb-status/attributes
    {:req-un [::nb-p-1/identifier
              ::nb-p-1/available
              ::nb-p-1/classes]
     :opt-un [::nb-p-1/device-path
              ::nb-p-1/port
              ::nb-p-1/additional-assets
              ::nb-p-1/interface
              ::nb-p-1/vendor
              ::nb-p-1/product
              ::nb-p-1/local-data-gateway-endpoint
              ::nb-p-1/raw-data-sample
              ::nb-p-1/serial-number
              ::nb-p-1/video-device
              ::nb-p-1/resources]}))
