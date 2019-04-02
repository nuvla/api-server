(ns sixsq.nuvla.server.resources.spec.session
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.session-template :as session-tpl]
    [sixsq.nuvla.server.resources.spec.ui-hints :as hints]
    [sixsq.nuvla.server.resources.spec.user :as user]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::method ::session-tpl/method)


;; reference to the session template that was used to create the session
(s/def ::template (s/merge
                    (s/keys :req-un [::session-tpl/href])
                    (s/map-of #{:href} any?)))


;; expiration time of the cookie
(s/def ::expiry ::cimi-core/timestamp)


;; identifier is optional as not all authentication methods use one
;; specified by the user in the authentication process
(s/def ::identifier string?)


;; user (a user resource identifier) is optional to allow for external
;; authentication methods to create a stub without having to know the
;; target user at the start of the process
(s/def ::user ::user/id)


;;TODO ACL rename roles in session spec
;; space-separated string of user's roles
(s/def ::roles ::cimi-core/nonblank-string)


(s/def ::server ::cimi-core/nonblank-string)


(s/def ::clientIP ::cimi-core/nonblank-string)


(s/def ::session
  (su/only-keys-maps c/common-attrs
                     {:req-un [::method ::template ::expiry]
                      :opt-un [::identifier ::user ::roles ::server ::clientIP ::hints/redirectURI]}))
