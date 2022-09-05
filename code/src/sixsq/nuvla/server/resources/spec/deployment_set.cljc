(ns sixsq.nuvla.server.resources.spec.deployment-set
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.util.spec :as su]
    [sixsq.nuvla.server.resources.spec.credential-template :as cred-spec]
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

(s/def ::applications
  (assoc (st/spec (s/coll-of ::module-id :min-count 1))
    :name "applications"
    :json-schema/type "array"

    :json-schema/display-name "applications"
    :json-schema/description "List of applications ids to deploy on targets."))

(s/def ::start
  (assoc (st/spec boolean?)
    :name "start"
    :json-schema/type "boolean"
    :json-schema/display-name "start"
    :json-schema/description "Start deployment automatically directly after creation"))

(s/def ::spec
  (assoc (st/spec (su/only-keys :req-un [::targets
                                         ::applications]
                                :opt-un [::start]))
    :name "spec"
    :json-schema/type "map"

    :json-schema/display-name "Spec"
    :json-schema/description "Deployment set spec"))

(def job-regex #"^job/[a-z0-9]+(-[a-z0-9]+)*$")
(s/def ::job-id (-> (st/spec (s/and string? #(re-matches job-regex %)))))

(s/def ::job
  (-> (st/spec ::job-id)
      (assoc :name "job"
             :json-schema/type "resource-id"

             :json-schema/display-name "job"
             :json-schema/description "last job id linked to the deployment-set")))

(def deployment-set-keys-spec
  (su/merge-keys-specs [common/common-attrs
                        {:req-un [::spec
                                  ::state]
                         :opt-un [::job]}]))


(s/def ::deployment-set (su/only-keys-maps deployment-set-keys-spec))
