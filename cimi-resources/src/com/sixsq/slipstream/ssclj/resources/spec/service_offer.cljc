(ns com.sixsq.slipstream.ssclj.resources.spec.service-offer
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.common :as c]
    [com.sixsq.slipstream.ssclj.resources.spec.connector-template :as ct]
    [com.sixsq.slipstream.ssclj.resources.spec.core :as cimi-core]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))

;;
;; For compute resources (VMs) the name of the connector must be connector document
;; id WITHOUT the 'connector/' prefix.  Changing this requires changes in much of
;; the downstream deployment code.
;;
;; For data resources, it is preferable to use the standard, full connector document
;; id.
;;
;; This schema now permits both, although the long-term goal should be a migration
;; towards the standard, full connector document id.
;;
(s/def ::href (s/or :short-id ::ct/identifier
                    :full-id ::cimi-core/resource-href))

(s/def ::connector (su/only-keys :req-un [::href]))

(s/def ::service-offer
  (su/constrained-map keyword? any?
                      c/common-attrs
                      {:req-un [::connector]}))
