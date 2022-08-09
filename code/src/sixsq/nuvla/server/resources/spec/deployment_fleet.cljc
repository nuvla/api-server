(ns sixsq.nuvla.server.resources.spec.deployment-fleet
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [sixsq.nuvla.server.resources.spec.credential-template :as cred-spec]
    [spec-tools.core :as st]))

(s/def ::state
  (-> (st/spec #{"CREATING", "CREATED",
                 "STARTING", "STARTED",
                 "STOPPING", "STOPPED"})
      (assoc :name "state"
             :json-schema/type "string"
             :json-schema/description "state of deployment fleet"

             :json-schema/value-scope {:values  ["CREATING", "CREATED",
                                                 "STARTING", "STARTED",
                                                 "STOPPING", "STOPPED"]})))

(s/def ::targets
  (-> (st/spec (s/coll-of ::cred-spec/credential-id))
      (assoc :name "targets"
             :json-schema/type "array"

             :json-schema/display-name "targets"
             :json-schema/description "List of targeted credentials ids.")))


(def ^:const module-id-regex #"^module/[0-9a-f]+(-[0-9a-f]+)*(_\d+)*$")
(defn module-id? [s] (re-matches module-id-regex s))
(s/def ::module-id (s/and string? module-id?))

(s/def ::applications
  (-> (st/spec (s/coll-of ::module-id))
      (assoc :name "applications"
             :json-schema/type "array"

             :json-schema/display-name "applications"
             :json-schema/description "List of applications ids to deploy on targets.")))

(s/def ::spec
  (-> (st/spec (su/only-keys :req-un [::targets
                                      ::applications]))
      (assoc :name "spec"
             :json-schema/type "map"

             :json-schema/display-name "Spec"
             :json-schema/description "Deployment fleet spec")))

(def deployment-fleet-keys-spec
  (su/merge-keys-specs [common/common-attrs
                        {:req-un [::spec
                                  ::state]
                         :opt-un []}]))


(s/def ::deployment-fleet (su/only-keys-maps deployment-fleet-keys-spec))
