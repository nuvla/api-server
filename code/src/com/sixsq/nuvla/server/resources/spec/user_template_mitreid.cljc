(ns com.sixsq.nuvla.server.resources.spec.user-template-mitreid
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.user-template :as ps]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(def user-template-mitreid-registration-keys-href
  {:opt-un [::ps/href]})


;; Defines the contents of the mitreid registration UserTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec))


;; Defines the contents of the mitreid registration template used in a create resource.
(s/def ::template
  (-> (st/spec (su/only-keys-maps ps/template-keys-spec
                                  user-template-mitreid-registration-keys-href))
      (assoc :name "template"
             :json-schema/type "map")))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
