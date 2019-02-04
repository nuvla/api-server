(ns com.sixsq.slipstream.ssclj.resources.spec.external-object-report
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.common :as c]
    [com.sixsq.slipstream.ssclj.resources.spec.core :as cimi-core]
    [com.sixsq.slipstream.ssclj.resources.spec.external-object :as eo]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))

(s/def ::runUUID ::cimi-core/nonblank-string)
(s/def ::component ::cimi-core/nonblank-string)

(def external-object-report-keys-spec
  (su/merge-keys-specs [eo/common-external-object-attrs
                        {:req-un [::runUUID
                                  ::component]}]))

(def resource-keys-spec
  (su/merge-keys-specs [c/common-attrs
                        external-object-report-keys-spec]))

(s/def ::external-object
  (su/only-keys-maps resource-keys-spec))
