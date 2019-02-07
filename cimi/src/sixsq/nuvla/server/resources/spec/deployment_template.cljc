(ns sixsq.nuvla.server.resources.spec.deployment-template
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::module ::cimi-common/resource-link)

(s/def ::keepRunning #{"Always",
                       "On Success",
                       "On Error",
                       "Never"})

(def ^:const parameter-name-regex #"^[a-zA-Z0-9]+([-_\.:][a-zA-Z0-9]*)*$")
(s/def ::parameter (s/and string? #(re-matches parameter-name-regex %)))
(s/def ::description ::cimi-core/nonblank-string)
(s/def ::value ::cimi-core/nonblank-string)

(s/def ::parameter-map (su/only-keys :req-un [::parameter]
                                     :opt-un [::description ::value]))

(s/def ::parameters (s/coll-of ::parameter-map :min-count 1 :kind vector?))

(s/def ::outputParameters ::parameters)

(def deployment-template-keys-spec {:req-un [::module
                                             ::outputParameters]
                                    :opt-un [::keepRunning]})

(s/def ::deployment-template (su/only-keys-maps
                               cimi-common/common-attrs
                               deployment-template-keys-spec))

;; Defines the contents of the generic template used in a create resource.
(s/def ::template
  (su/only-keys-maps cimi-common/template-attrs
                     deployment-template-keys-spec))

(s/def ::deployment-template-create
  (su/only-keys-maps cimi-common/create-attrs
                     {:req-un [::template]}))
