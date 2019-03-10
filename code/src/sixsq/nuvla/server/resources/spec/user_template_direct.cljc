(ns sixsq.nuvla.server.resources.spec.user-template-direct
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.user :as user]
    [sixsq.nuvla.server.resources.spec.user-template :as user-template]
    [sixsq.nuvla.server.util.spec :as su]))


(def user-template-direct-keys
  {:opt-un [::user/href
            ::user/password
            ::user/isSuperUser
            ::user/state
            ::user/deleted]})

(def user-template-keys-spec-req
  (su/merge-keys-specs
    [user/user-keys-spec user-template-direct-keys]))

(def user-template-create-keys-spec-req user-template-keys-spec-req)

;; Defines the contents of the direct UserTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps user-template/resource-keys-spec
                     user-template-keys-spec-req))

;; Defines the contents of the direct template used in a create resource.
(s/def ::template
  (su/only-keys-maps user-template/template-keys-spec
                     user-template-create-keys-spec-req))

(s/def ::schema-create
  (su/only-keys-maps user-template/create-keys-spec
                     {:opt-un [::template]}))
