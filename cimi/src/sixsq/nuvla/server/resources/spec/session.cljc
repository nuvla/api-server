(ns sixsq.nuvla.server.resources.spec.session
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.session-template :as session-tpl]
    [sixsq.nuvla.server.resources.spec.ui-hints :as hints]
    [sixsq.nuvla.server.util.spec :as su]))

(s/def ::method ::session-tpl/method)

;; reference to the session template that was used to create the session
(s/def ::template (s/merge
                    (s/keys :req-un [::session-tpl/href])
                    (s/map-of #{:href} any?)))

;; expiration time of the cookie
(s/def ::expiry ::cimi-core/timestamp)

;; username is optional to support external authentication methods
;; that usually require creation of stub session for later validation
(s/def ::username ::cimi-core/nonblank-string)

;; space-separated string of user's roles
(s/def ::roles ::cimi-core/nonblank-string)

(s/def ::server ::cimi-core/nonblank-string)
(s/def ::clientIP ::cimi-core/nonblank-string)

(s/def ::session
  (su/only-keys-maps c/common-attrs
                     {:req-un [::method ::template ::expiry]
                      :opt-un [::username ::roles ::server ::clientIP ::hints/redirectURI]}))
