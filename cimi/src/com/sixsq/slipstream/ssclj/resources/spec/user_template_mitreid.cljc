(ns com.sixsq.slipstream.ssclj.resources.spec.user-template-mitreid
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.user-template :as ps]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))


(def user-template-mitreid-registration-keys-href
  {:opt-un [::ps/href]})


;; Defines the contents of the mitreid-registration UserTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec))


;; Defines the contents of the mitreid-registration template used in a create resource.
;; NOTE: The name must match the key defined by the resource, :userTemplate here.
(s/def ::userTemplate
  (su/only-keys-maps ps/template-keys-spec
                     user-template-mitreid-registration-keys-href))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::userTemplate]}))
