(ns sixsq.nuvla.server.resources.spec.deployment-set
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

(s/def ::applications
  (assoc (st/spec (s/coll-of ::module-id :min-count 1))
    :name "applications"
    :json-schema/type "array"

    :json-schema/display-name "applications"
    :json-schema/description "List of applications ids to deploy on targets."))

(s/def ::application
  (assoc (st/spec ::module-id)
    :name "application"
    :json-schema/description "application id"))

(s/def ::environmental-variable
  (assoc (st/spec (su/only-keys :req-un [::container-spec/name
                                         ::container-spec/value
                                         ::application]))
    :name "environmental-variable"
    :json-schema/type "map"
    :json-schema/description
    "environmental variable name, value and application"))

(s/def ::env
  (assoc (st/spec (s/coll-of ::environmental-variable :kind vector?))
    :name "env"
    :json-schema/type "array"
    :json-schema/display-name "environmental variables"
    :json-schema/description "list of environmental variable to be overwritten"))

(s/def ::code
  (assoc (st/spec ::core/nonblank-string)
    :name "code"
    :json-schema/description "coupon code"))

(s/def ::coupon
  (assoc (st/spec (su/only-keys :req-un [::code
                                         ::application]))
    :name "environmental-variable"
    :json-schema/type "map"
    :json-schema/description
    "environmental variable name, value and application"))

(s/def ::coupons
  (assoc (st/spec (s/coll-of ::coupon :kind vector?))
    :name "coupons"
    :json-schema/type "array"
    :json-schema/display-name "coupons"
    :json-schema/description "list of coupon to apply per application"))

(s/def ::start
  (assoc (st/spec boolean?)
    :name "start"
    :json-schema/type "boolean"
    :json-schema/display-name "start"
    :json-schema/description "Start deployment automatically directly after creation"))

(s/def ::spec
  (assoc (st/spec (su/only-keys :req-un [::targets
                                         ::applications]
                                :opt-un [::start
                                         ::env
                                         ::coupons]))
    :name "spec"
    :json-schema/type "map"

    :json-schema/display-name "Spec"
    :json-schema/description "Deployment set spec"))

(def deployment-set-keys-spec
  (su/merge-keys-specs [common/common-attrs
                        {:req-un [::spec
                                  ::state]}]))


(s/def ::deployment-set (su/only-keys-maps deployment-set-keys-spec))
