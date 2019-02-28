(ns sixsq.nuvla.server.resources.spec.data-object-generic
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.data-object :as do]
    [sixsq.nuvla.server.util.spec :as su]))

(def data-object-generic-keys-spec do/common-data-object-attrs)

(def resource-keys-spec
  (su/merge-keys-specs [c/common-attrs
                        data-object-generic-keys-spec]))

(s/def ::data-object
  (su/only-keys-maps resource-keys-spec))
