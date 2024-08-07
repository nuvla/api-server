(ns com.sixsq.nuvla.server.resources.spec.module-applications-sets
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.container :as container-spec]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.resources.spec.deployment :as deployment]
    [com.sixsq.nuvla.server.resources.spec.module-application :as module-application]
    [com.sixsq.nuvla.server.resources.spec.module-component :as module-component]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

(s/def ::name
  (assoc (st/spec ::core/nonblank-string)
    :name "name"
    :json-schema/type "string"))

(s/def ::description
  (assoc (st/spec string?)
    :name "description"
    :json-schema/type "string"))

(def ^:const module-id-regex #"^module/[a-zA-Z0-9-]+$")

(defn module-id? [s] (re-matches module-id-regex s))

(s/def ::id
  (assoc (st/spec (s/and string? module-id?))
    :name "id"
    :json-schema/type "string"))

(s/def ::version
  (assoc (st/spec nat-int?)
    :name "version"
    :json-schema/type "integer"))

(s/def ::value
  (-> (st/spec string?)
      (assoc :name "blankable value override"
             :json-schema/description "blankable parameter value override")))

(s/def ::environmental-variable
  (assoc (st/spec (su/only-keys :req-un [::container-spec/name
                                         ::value]))
    :name "environmental-variable"
    :json-schema/type "map"
    :json-schema/description
    "environmental variable name, value and application"))

(s/def ::environmental-variables
  (assoc (st/spec (s/coll-of ::environmental-variable))
    :name "environmental-variables"
    :json-schema/type "array"
    :json-schema/display-name "environmental variables"
    :json-schema/description "list of environmental variable to be overwritten"))

(s/def ::application
  (assoc (st/spec (su/only-keys :req-un [::id
                                         ::version]
                                :opt-un [::environmental-variables
                                         ::deployment/registries-credentials
                                         ::module-application/files]))
    :name "application"
    :json-schema/type "map"))

(s/def ::applications
  (assoc (st/spec (s/coll-of ::application))
    :name "applications"
    :json-schema/type "array"))

(s/def ::subtype
  (-> (st/spec #{"docker" "kubernetes"})
      (assoc :name "subtype"
             :json-schema/type "string"
             :json-schema/value-scope {:values ["docker" "kubernetes"]}
             :json-schema/description "subtype of applications")))

(s/def ::applications-set
  (assoc (st/spec (su/only-keys :req-un [::name]
                                :opt-un [::subtype
                                         ::applications
                                         ::description]))
    :name "applications-set"
    :json-schema/type "map"))

(s/def ::applications-sets
  (assoc (st/spec (s/coll-of ::applications-set :min-count 1))
    :name "applications-sets"
    :json-schema/type "array"))

(def module-application-keys-spec (su/merge-keys-specs
                                    [common/common-attrs
                                     {:req-un [::module-component/author
                                               ::applications-sets]
                                      :opt-un [::module-component/commit]}]))


(s/def ::schema (su/only-keys-maps module-application-keys-spec))
