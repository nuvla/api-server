(ns sixsq.nuvla.server.resources.spec.configuration-template-nuvla
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.configuration-template :as ps]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::support-email ::cimi-core/nonblank-string)


(s/def ::smtp-username ::cimi-core/nonblank-string)


(s/def ::smtp-password ::cimi-core/nonblank-string)


(s/def ::smtp-host ::cimi-core/nonblank-string)


(s/def ::smtp-port ::cimi-core/port)


(s/def ::smtp-ssl boolean?)


(s/def ::smtp-debug boolean?)


(def configuration-template-keys-spec-req
  {:req-un [::smtp-username
            ::smtp-password
            ::smtp-host
            ::smtp-port
            ::smtp-ssl
            ::smtp-debug]
   :opt-un [::support-email]})


;; FIXME: Treats all parameters as optional.  Instead those without reasonable defaults should be required.
(def configuration-template-keys-spec-opt
  {:opt-un (concat (:req-un configuration-template-keys-spec-req)
                   (:opt-un configuration-template-keys-spec-req))})


;; Defines the contents of the slipstream ConfigurationTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     configuration-template-keys-spec-req))


;; Defines the contents of the slipstream template used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec
                     configuration-template-keys-spec-opt))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:opt-un [::template]}))
