(ns sixsq.nuvla.server.resources.spec.session
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.session-template :as session-tpl]
    [sixsq.nuvla.server.resources.spec.ui-hints :as hints]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::method ::session-tpl/method)


;; reference to the session template that was used to create the session
(s/def ::template (-> (st/spec
                        (s/merge
                          (s/keys :req-un [::session-tpl/href])
                          (s/map-of #{:href} any?)))
                      (assoc :name "template"
                             :json-schema/type "map")))


;; expiration time of the cookie
(s/def ::expiry ::core/timestamp)


;; identifier is optional as not all authentication methods use one
;; specified by the user in the authentication process
(s/def ::identifier string?)


;; user (a user resource identifier) is optional to allow for external
;; authentication methods to create a stub without having to know the
;; target user at the start of the process
(s/def ::user ::core/nonblank-string)


;;TODO ACL rename roles in session spec
;; space-separated string of user's roles
(s/def ::roles ::core/nonblank-string)


(s/def ::groups ::core/nonblank-string)


(s/def ::server ::core/nonblank-string)


(s/def ::client-ip ::core/nonblank-string)


(s/def ::active-claim (-> (st/spec (s/nilable ::core/nonblank-string))
                          (assoc :name "active-claim"
                                 :json-schema/type "string"
                                 :json-schema/description "current active claim")))


(s/def ::session
  (su/only-keys-maps common/common-attrs
                     {:req-un [::method ::template ::expiry]
                      :opt-un [::identifier
                               ::user
                               ::roles
                               ::server
                               ::client-ip
                               ::hints/redirect-url
                               ::active-claim
                               ::groups]}))
