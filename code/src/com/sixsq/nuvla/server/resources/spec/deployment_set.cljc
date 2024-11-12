(ns com.sixsq.nuvla.server.resources.spec.deployment-set
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.deployment-set.utils :as utils]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.resources.spec.credential-template :as cred-spec]
    [com.sixsq.nuvla.server.resources.spec.deployment-set-operational-status :as os]
    [com.sixsq.nuvla.server.resources.spec.module-applications-sets :as module-sets]
    [com.sixsq.nuvla.server.resources.spec.deployment :as deployment]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

(s/def ::state
  (assoc (st/spec (set utils/states))
    :name "state"
    :json-schema/type "string"
    :json-schema/description "state of deployment set"

    :json-schema/value-scope {:values utils/states}))

(s/def ::targets
  (assoc (st/spec (s/coll-of ::cred-spec/credential-id :min-count 1))
    :name "targets"
    :json-schema/type "array"

    :json-schema/display-name "targets"
    :json-schema/description "List of targeted credentials ids."))

(s/def ::fleet
  (assoc (st/spec (s/coll-of string?))
         :name "fleet"
         :json-schema/type "array"
         :json-schema/display-name "fleet"
         :json-schema/description "List of targeted edge ids."))

(s/def ::fleet-filter
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "fleet-filter"
             :json-schema/type "string"

             :json-schema/display-name "fleet filter"
             :json-schema/description "filter for fleet associated with this deployment set")))

(s/def ::start
  (assoc (st/spec boolean?)
    :name "start"
    :json-schema/type "boolean"
    :json-schema/display-name "start"
    :json-schema/description "Start deployment automatically directly after creation"))

(s/def ::set-overwrites
  (assoc (st/spec (su/only-keys :opt-un [::module-sets/applications
                                         ::targets
                                         ::fleet
                                         ::fleet-filter]))
    :name "set-overwrites"
    :json-schema/type "map"))

(s/def ::overwrites
  (assoc (st/spec (s/coll-of ::set-overwrites :min-count 1))
    :name "overwrites"
    :json-schema/type "array"))

;; id is immutable, edition possible only at creation time
(s/def ::applications-sets-config
  (assoc (st/spec (su/only-keys :req-un [::module-sets/id
                                         ::module-sets/version
                                         ::overwrites]))
    :json-schema/type "map"))

(s/def ::applications-sets
  (assoc (st/spec (s/coll-of ::applications-sets-config :min-count 1 :max-count 1))
    :name "applications-sets"
    :json-schema/type "array"
    :json-schema/display-name "applications sets"))

(s/def ::auto-update
  (assoc (st/spec boolean?)
    :name "auto-update"
    :json-schema/type "boolean"
    :json-schema/display-name "auto update"
    :json-schema/description "Auto update deployment group automatically at regular intervals"))

(s/def ::auto-update-interval
  (assoc (st/spec pos-int?)
    :name "auto-update-interval"
    :json-schema/type "integer"
    :json-schema/display-name "auto update interval"
    :json-schema/description "Auto update interval in minutes"))

(s/def ::next-refresh
  (assoc (st/spec ::core/timestamp)
    :name "next-refresh"
    :json-schema/type "date-time"
    :json-schema/display-name "next refresh"
    :json-schema/description "Time of the next refresh"))

(def deployment-set-keys-spec
  (su/merge-keys-specs [common/common-attrs
                        {:req-un [::state
                                  ::applications-sets]
                         :opt-un [::start
                                  ::os/operational-status
                                  ::deployment/api-endpoint
                                  ::auto-update
                                  ::auto-update-interval
                                  ::next-refresh]}]))


(s/def ::deployment-set (su/only-keys-maps deployment-set-keys-spec))