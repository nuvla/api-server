(ns sixsq.nuvla.server.resources.spec.infrastructure
    (:require
           [clojure.spec.alpha :as s]
           [sixsq.nuvla.server.resources.spec.common :as c]
           [sixsq.nuvla.server.resources.spec.core :as cimi-core]
           [sixsq.nuvla.server.resources.spec.infrastructure-template :as infrastructure-tpl]
           [sixsq.nuvla.server.resources.spec.ui-hints :as hints]
           [sixsq.nuvla.server.util.spec :as su]))

(s/def ::type ::infrastructure-tpl/type)

;; reference to the template that was used to create the infrastructure
(s/def ::template (s/merge
                         (s/keys :req-un [::infrastructure-tpl/href])
                         (s/map-of #{:href} any?)))

(def infrastructure-keys-spec (su/merge-keys-specs [c/common-attrs
                                                {:req-un [::type
                                                          ::template]}]))
