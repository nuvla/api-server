(ns sixsq.nuvla.server.resources.spec.module-application
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.container :as container]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.module-component :as module-component]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::docker-compose
  (->
    (st/spec ::core/nonblank-string)
    (assoc :name "docker-compose"
           :json-schema/description "Text in yaml format for docker-compose or kubernetes manifest"
           :json-schema/indexed false

           :json-schema/fulltext true)))


(s/def ::infra-subtype
  (->
    (st/spec #{"swarm" "kubernetes"})
    (assoc :name "infra-subtype"
           :json-schema/type "string"
           :json-schema/description "Should be used on infrastructure service of specified subtype"
           :json-schema/value-scope {:values  ["swarm" "kubernetes"]
                                     :default "swarm"}
           :json-schema/fulltext true)))


(s/def ::file-content
  (-> (st/spec string?)
      (assoc :name "file-content"
             :json-schema/indexed false
             :json-schema/fulltext true)))


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



(def module-application-keys-spec (su/merge-keys-specs
                                    [common/common-attrs
                                     {:req-un [::docker-compose
                                               ::module-component/author]
                                      :opt-un [::module-component/commit
                                               ::module-component/urls
                                               ::module-component/output-parameters
                                               ::container/environmental-variables
                                               ::files
                                               ::infra-subtype]}]))


(s/def ::schema (su/only-keys-maps module-application-keys-spec))
