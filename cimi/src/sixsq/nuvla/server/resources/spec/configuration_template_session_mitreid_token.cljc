(ns sixsq.nuvla.server.resources.spec.configuration-template-session-mitreid-token
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.configuration-template :as ps]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::clientIPs (s/coll-of ::cimi-core/token :min-count 1 :kind vector?))


(def configuration-template-keys-spec-req
  {:req-un [::ps/instance]
   :opt-un [::clientIPs]})


(def configuration-template-keys-spec-create
  {:req-un [::ps/instance]
   :opt-un [::clientIPs]})


;; Defines the contents of the OIDC authentication ConfigurationTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     configuration-template-keys-spec-req))


;; Defines the contents of the OIDC authentication template used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec
                     configuration-template-keys-spec-create))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
