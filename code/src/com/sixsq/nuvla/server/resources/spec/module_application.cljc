(ns com.sixsq.nuvla.server.resources.spec.module-application
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.container :as container]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.resources.spec.deployment :as deployment]
    [com.sixsq.nuvla.server.resources.spec.module-component :as module-component]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::docker-compose
  (->
    (st/spec ::core/nonblank-string)
    (assoc :name "docker-compose"
           :json-schema/type "free-text"
           :json-schema/indexed false
           :json-schema/description "Text in yaml format for docker-compose or kubernetes manifest")))


(s/def ::file-content
  (-> (st/spec string?)
      (assoc :name "file-content"
             :json-schema/type "free-text"
             :json-schema/indexed false)))


(s/def ::file-name
  (-> (st/spec ::core/filename)
      (assoc :name "file-name")))


(s/def ::file
  (-> (st/spec (su/only-keys :req-un [::file-name ::file-content]))
      (assoc :name "file"
             :json-schema/type "map")))


(s/def ::files
  (-> (st/spec (s/coll-of ::file :kind vector? :min-count 1))
      (assoc :name "files"
             :json-schema/type "array"
             :json-schema/description "file to be used with configs and secrets")))


(s/def ::unsupported-options
  (-> (st/spec (s/coll-of string? :kind vector?))
    (assoc :name "unsupported options"
           :json-schema/type "array"
           :json-schema/description "unsupported options for swarm in compose file"
           :json-schema/server-managed true
           :json-schema/editable false)))


(s/def ::requires-user-rights
  (-> (st/spec boolean?)
      (assoc :name "requires-user-rights"
             :json-schema/type "boolean"
             :json-schema/description "deployments of this module require user rights")))


(def module-application-keys-spec (su/merge-keys-specs
                                    [common/common-attrs
                                     {:req-un [::docker-compose
                                               ::module-component/author]
                                      :opt-un [::module-component/commit
                                               ::module-component/urls
                                               ::module-component/output-parameters
                                               ::module-component/architectures
                                               ::module-component/minimum-requirements
                                               ::container/environmental-variables
                                               ::container/private-registries
                                               ::deployment/registries-credentials
                                               ::files
                                               ::requires-user-rights

                                               ;;deprecated
                                               ::unsupported-options]}]))


(s/def ::schema (su/only-keys-maps module-application-keys-spec))
