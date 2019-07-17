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
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "docker-compose"
             :json-schema/description "docker-compose file in yaml format"
             :json-schema/indexed false

             :json-schema/fulltext true)))


(def module-application-keys-spec (su/merge-keys-specs
                                    [common/common-attrs
                                     {:req-un [::docker-compose
                                               ::module-component/author]
                                      :opt-un [::module-component/commit
                                               ::module-component/urls
                                               ::module-component/output-parameters
                                               ::container/environmental-variables]}]))


(s/def ::schema (su/only-keys-maps module-application-keys-spec))
