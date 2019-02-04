(ns com.sixsq.slipstream.ssclj.resources.spec.user-params-template
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.common :as cimi-common]
    [com.sixsq.slipstream.ssclj.resources.spec.core :as cimi-core]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))

(s/def :cimi.user-params-template/paramsType ::cimi-core/nonblank-string)

(def user-params-template-keys-spec
  {:req-un [:cimi.user-params-template/paramsType]})

(def user-params-template-keys-spec-opt
  {:opt-un [:cimi.user-params-template/paramsType]})

(def resource-keys-spec
  (su/merge-keys-specs [cimi-common/common-attrs
                        user-params-template-keys-spec]))

(def create-keys-spec
  (su/merge-keys-specs [cimi-common/create-attrs]))

;; subclasses MUST provide the href to the template to use
(def template-keys-spec
  (su/merge-keys-specs [cimi-common/template-attrs
                        user-params-template-keys-spec-opt]))

