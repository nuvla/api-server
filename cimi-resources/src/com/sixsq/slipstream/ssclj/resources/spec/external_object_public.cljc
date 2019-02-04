(ns com.sixsq.slipstream.ssclj.resources.spec.external-object-public
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.common :as c]
    [com.sixsq.slipstream.ssclj.resources.spec.core :as cimi-core]
    [com.sixsq.slipstream.ssclj.resources.spec.external-object :as eo]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))


(s/def ::URL ::cimi-core/nonblank-string)

(def external-object-public-keys-spec
  (su/merge-keys-specs [eo/common-external-object-attrs
                        {:opt-un [::URL]}]))

(def resource-keys-spec
  (su/merge-keys-specs [c/common-attrs
                        external-object-public-keys-spec]))

(s/def ::external-object
  (su/only-keys-maps resource-keys-spec))
