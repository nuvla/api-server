(ns sixsq.nuvla.server.resources.spec.data-object-public
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.data-object :as do]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::url ::core/nonblank-string)

(def data-object-public-keys-spec
  (su/merge-keys-specs [do/common-data-object-attrs
                        {:opt-un [::url]}]))

(def resource-keys-spec
  (su/merge-keys-specs [common/common-attrs
                        data-object-public-keys-spec]))

(s/def ::schema
  (su/only-keys-maps resource-keys-spec))
