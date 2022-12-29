(ns sixsq.nuvla.server.resources.spec.module-applications-sets
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.module-component :as module-component]
    [sixsq.nuvla.server.util.spec :as su]
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


(s/def ::application
  (assoc (st/spec (su/only-keys :req-un [::id
                                         ::version]
                                ;:opt-un [::env]
                                ))
    :name "application"
    :json-schema/type "map"))

(s/def ::applications
  (assoc (st/spec (s/coll-of ::application :min-count 1))
    :name "applications"
    :json-schema/type "array"))

(s/def ::applications-set
  (assoc (st/spec (su/only-keys :req-un [::name ::applications]
                                :opt-un [::description]))
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
