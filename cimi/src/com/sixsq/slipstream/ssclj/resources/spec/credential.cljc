(ns com.sixsq.slipstream.ssclj.resources.spec.credential
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.common :as c]
    [com.sixsq.slipstream.ssclj.resources.spec.credential-template :as ct]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))


(s/def ::type ::ct/type)


(s/def ::method ::ct/method)


(def credential-keys-spec (su/merge-keys-specs [c/common-attrs
                                                {:req-un [::type
                                                          ::method]}]))
