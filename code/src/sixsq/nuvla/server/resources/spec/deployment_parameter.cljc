(ns sixsq.nuvla.server.resources.spec.deployment-parameter
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::deployment ::core/resource-link)

(s/def ::node-id ::core/token)

(s/def ::name ::core/token)

(s/def ::value ::core/nonblank-string)

(def deployment-parameter-keys-spec
  (su/merge-keys-specs [common/common-attrs
                        {:req-un [::deployment ::name]
                         :opt-un [::node-id ::value]}]))

(s/def ::deployment-parameter (su/only-keys-maps deployment-parameter-keys-spec))
