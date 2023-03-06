(ns sixsq.nuvla.server.resources.spec.deployment-set-v2
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.container :as container-spec]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.credential-template :as cred-spec]
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

(def ^:const module-id-regex #"^module/[0-9a-f]+(-[0-9a-f]+)*(_\d+)*$")
(defn module-id? [s] (re-matches module-id-regex s))
(s/def ::module-id (s/and string? module-id?))

(s/def ::start
  (assoc (st/spec boolean?)
    :name "start"
    :json-schema/type "boolean"
    :json-schema/display-name "start"
    :json-schema/description "Start deployment automatically directly after creation"))


(s/def ::id
  (assoc (st/spec (s/and string? module-id?))
    :name "id"
    :json-schema/type "string"))

(s/def ::version
  (assoc (st/spec nat-int?)
    :name "version"
    :json-schema/type "integer"))

;; id is immutable, edition possible only at creation time
(s/def ::applications-sets (assoc (st/spec (su/only-keys :req-un [::id
                                                                  ::version]))
                             :name "applications-sets"
                             :json-schema/type "map"
                             :json-schema/display-name "applications sets"))


(s/def ::deployment-set (assoc (st/spec (su/only-keys :req-un [::state
                                                               ::targets
                                                               ::applications-sets]
                                                      :opt-un [::start]))
                          :name "spec"
                          :json-schema/type "map"

                          :json-schema/display-name "Spec"
                          :json-schema/description "Deployment set spec"))
