(ns com.sixsq.slipstream.ssclj.resources.spec.user-template-direct
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.user :as user]
    [com.sixsq.slipstream.ssclj.resources.spec.user-template :as user-template]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))


(def user-template-direct-keys
  {:opt-un [::user/href
            ::user/password
            ::user/roles
            ::user/isSuperUser
            ::user/state
            ::user/deleted
            ::user/creation
            ::user/lastOnline
            ::user/lastExecute
            ::user/activeSince
            ::user/githublogin
            ::user/cyclonelogin]})

(def user-template-keys-spec-req
  (su/merge-keys-specs
    [user/user-keys-spec user-template-direct-keys]))

(def user-template-create-keys-spec-req user-template-keys-spec-req)

;; Defines the contents of the direct UserTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps user-template/resource-keys-spec
                     user-template-keys-spec-req))

;; Defines the contents of the direct template used in a create resource.
;; NOTE: The name must match the key defined by the resource, :userTemplate here.
(s/def ::userTemplate
  (su/only-keys-maps user-template/template-keys-spec
                     user-template-create-keys-spec-req))

(s/def ::schema-create
  (su/only-keys-maps user-template/create-keys-spec
                     {:opt-un [::userTemplate]}))
