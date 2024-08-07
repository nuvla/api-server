(ns com.sixsq.nuvla.server.resources.spec.data-object-generic
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.data-object :as do]
    [com.sixsq.nuvla.server.util.spec :as su]))


(def data-object-generic-keys-spec do/common-data-object-attrs)


(def resource-keys-spec
  (su/merge-keys-specs [common/common-attrs
                        data-object-generic-keys-spec]))


(s/def ::schema
  (su/only-keys-maps resource-keys-spec))
