(ns sixsq.nuvla.server.resources.spec.event
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]))

;;
;; severity and type values that are reused elsewhere
;;

(def ^:const severity-critical "critical")
(def ^:const severity-high "high")
(def ^:const severity-medium "medium")
(def ^:const severity-low "low")

(def ^:const type-state "state")
(def ^:const type-alarm "alarm")
(def ^:const type-action "action")
(def ^:const type-system "system")


(s/def ::severity (s/and string? #{severity-critical
                                   severity-high
                                   severity-medium
                                   severity-low}))


(s/def ::type (s/and string? #{type-state
                               type-alarm
                               type-action
                               type-system}))

;; Events may need to reference resources that do not follow the CIMI.
;; conventions.  Allow for a more flexible schema to be used here.
(s/def ::href (s/and string? #(re-matches #"^[a-zA-Z0-9]+[a-zA-Z0-9_./-]*$" %)))
(s/def ::resource-link (s/keys :req-un [::href]))

(s/def ::state string?)
(s/def ::resource ::resource-link)
(s/def ::content (su/only-keys :req-un [::resource ::state]))

(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::core/timestamp
                               ::content
                               ::type
                               ::severity]}))
