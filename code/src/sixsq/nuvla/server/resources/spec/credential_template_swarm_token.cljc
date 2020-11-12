(ns sixsq.nuvla.server.resources.spec.credential-template-swarm-token
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::scope
  (-> (st/spec #{"MANAGER" "WORKER"})
      (assoc :name "scope"
             :json-schema/type "string"
             :json-schema/description "scope of the given Swarm token (MANAGER or WORKER)"
             :json-schema/value-scope {:values ["MANAGER", "WORKER"]}

             :json-schema/order 30)))


(s/def ::token
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "token"
             :json-schema/description "Swarm token"

             :json-schema/order 31
             :json-schema/sensitive true)))


(def credential-template-keys-spec
  {:opt-un [::scope ::token]})


(def credential-template-create-keys-spec
  {:req-un [::scope ::token]})


;; Defines the contents of the resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     credential-template-keys-spec))


;; Defines the contents of the template used in a create resource.
(s/def ::template
  (-> (st/spec (su/only-keys-maps ps/template-keys-spec
                                  credential-template-create-keys-spec))
      (assoc :name "template"
             :json-schema/type "map")))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
