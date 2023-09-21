(ns sixsq.nuvla.server.resources.spec.deployment-set-operational-status
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.deployment-set.utils :as utils]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.deployment :as deployment]
    [sixsq.nuvla.server.resources.spec.module-applications-sets :as module-sets]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

(s/def ::target
  (assoc (st/spec ::core/resource-href)
    :json-schema/type "string"
    :json-schema/description "operational status target"))

(s/def ::app-set
  (assoc (st/spec ::core/nonblank-string)
    :json-schema/type "string"
    :json-schema/description "operational status application set name"))

(s/def ::current-deployment
  (assoc (st/spec (su/only-keys :req-un [::common/id
                                         ::module-sets/application
                                         ::app-set
                                         ::target
                                         ::deployment/state]))
    :json-schema/type "map"
    :json-schema/description "current deployment"))

(s/def ::deployment-to-add
  (assoc (st/spec (su/only-keys :req-un [::target
                                         ::module-sets/application
                                         ::app-set]))
    :json-schema/type "map"
    :json-schema/description "target deployment"))

(s/def ::deployments-to-add
  (assoc (st/spec (s/coll-of ::deployment-to-add))
    :json-schema/type "array"
    :json-schema/description "deployments to add"
    :json-schema/indexed false))

(s/def ::deployments-to-remove
  (assoc (st/spec (s/coll-of ::common/id))
    :json-schema/type "array"
    :json-schema/description "deployments to remove"
    :json-schema/indexed false))

(s/def ::current-or-target-deployment
  (assoc (st/spec (su/only-keys :req-un [::module-sets/application
                                         ::app-set
                                         ::target]
                                :opt-un [::common/id
                                         ::deployment/state]))
      :json-schema/type "map"
      :json-schema/description "current or target deployment"))

(s/def ::deployment-to-update
  (assoc (st/spec (s/coll-of ::current-or-target-deployment :min-count 2 :max-count 2))
    :json-schema/type "array"
    :json-schema/description "deployment to update"))

(s/def ::deployments-to-update
  (assoc (st/spec (s/coll-of ::deployment-to-update))
    :json-schema/type "array"
    :json-schema/description "deployments to update"
    :json-schema/indexed false))

(s/def ::status
  (assoc (st/spec (set utils/operational-statuses))
    :json-schema/type "string"
    :json-schema/description "operational status overall outcome"

    :json-schema/value-scope {:values utils/operational-statuses}))

(s/def ::operational-status
  (assoc (st/spec (su/only-keys :req-un [::status]
                                :opt-un [::deployments-to-add
                                         ::deployments-to-remove
                                         ::deployments-to-update]))
    :json-schema/type "map"
    :json-schema/description "operational status of deployment set"))
