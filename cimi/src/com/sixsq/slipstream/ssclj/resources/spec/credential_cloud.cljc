(ns com.sixsq.slipstream.ssclj.resources.spec.credential-cloud
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.credential :as cred]
    [com.sixsq.slipstream.ssclj.resources.spec.credential-template]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))


(s/def ::disabledMonitoring boolean?)


(def credential-keys-spec (su/merge-keys-specs [cred/credential-keys-spec
                                                {:opt-un [::disabledMonitoring]}]))
