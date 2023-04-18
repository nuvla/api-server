(ns sixsq.nuvla.server.resources.spec.deployment-set
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.module-applications-sets :as module-sets]
    [sixsq.nuvla.server.resources.spec.credential-template :as cred-spec]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

(s/def ::state
  (assoc (st/spec #{"CREATING", "CREATED",
                    "STARTING", "STARTED",
                    "STOPPING", "STOPPED"})
    :name "state"
    :json-schema/type "string"
    :json-schema/description "state of deployment set"

    :json-schema/value-scope {:values ["CREATING", "CREATED",
                                       "STARTING", "STARTED",
                                       "STOPPING", "STOPPED"]}))

(s/def ::targets
  (assoc (st/spec (s/coll-of ::cred-spec/credential-id :min-count 1))
    :name "targets"
    :json-schema/type "array"

    :json-schema/display-name "targets"
    :json-schema/description "List of targeted credentials ids."))

(s/def ::start
  (assoc (st/spec boolean?)
    :name "start"
    :json-schema/type "boolean"
    :json-schema/display-name "start"
    :json-schema/description "Start deployment automatically directly after creation"))

(s/def ::set-overwrites
  (assoc (st/spec (su/only-keys :opt-un [::module-sets/applications
                                         ::targets]))
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

(def deployment-set-keys-spec
  (su/merge-keys-specs [common/common-attrs
                        {:req-un [::state
                                  ::applications-sets]
                         :opt-un [::start]}]))


(s/def ::deployment-set (su/only-keys-maps deployment-set-keys-spec))