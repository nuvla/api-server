(ns sixsq.nuvla.server.resources.spec.event
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]))

(s/def ::severity (s/and string? #{event-utils/severity-critical
                                   event-utils/severity-high
                                   event-utils/severity-medium
                                   event-utils/severity-low}))

(s/def ::type (s/and string? #{event-utils/type-state
                               event-utils/type-alarm
                               event-utils/type-action
                               event-utils/type-system}))

;; Events may need to reference resources that do not follow the CIMI.
;; conventions.  Allow for a more flexible schema to be used here.
(s/def ::href (s/and string? #(re-matches #"^[a-zA-Z0-9]+[a-zA-Z0-9_./-]*$" %)))
(s/def ::resource-link (s/keys :req-un [::href]))

(s/def ::state string?)
(s/def ::resource ::resource-link)
(s/def ::content (su/only-keys :req-un [::resource ::state]))

(s/def ::event
  (su/only-keys :req-un [::cimi-common/id
                         ::cimi-common/resource-type
                         ::cimi-common/acl

                         ::cimi-core/timestamp
                         ::content
                         ::type
                         ::severity]
                :opt-un [::cimi-common/created              ;; FIXME: should be required
                         ::cimi-common/updated              ;; FIXME: should be required
                         ::cimi-common/name
                         ::cimi-common/description
                         ::cimi-common/tags
                         ::cimi-common/operations]))
