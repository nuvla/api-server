(ns sixsq.nuvla.server.resources.spec.user-params-template-exec
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.user-params-template :as ps]
    [sixsq.nuvla.server.util.spec :as su]))

(s/def :cimi.user-params-template-exec/defaultCloudService string?)
(s/def :cimi.user-params-template-exec/sshPublicKey string?)
(s/def :cimi.user-params-template-exec/keepRunning ::cimi-core/nonblank-string)
(s/def :cimi.user-params-template-exec/mailUsage ::cimi-core/nonblank-string)
(s/def :cimi.user-params-template-exec/verbosityLevel int?)
(s/def :cimi.user-params-template-exec/timeout int?)

(def user-params-template-exec-keys
  [:cimi.user-params-template-exec/sshPublicKey
   :cimi.user-params-template-exec/defaultCloudService
   :cimi.user-params-template-exec/verbosityLevel
   :cimi.user-params-template-exec/keepRunning
   :cimi.user-params-template-exec/mailUsage
   :cimi.user-params-template-exec/timeout])

(def user-params-template-exec-keys-spec
  {:req-un user-params-template-exec-keys})

(def user-params-template-exec-keys-spec-opt
  {:opt-un user-params-template-exec-keys})

;; Defines the contents of the execution UserParamTemplate resource itself.
(s/def :cimi/user-params-template.exec
  (su/only-keys-maps ps/resource-keys-spec
                     user-params-template-exec-keys-spec))

;; Defines the contents of the auto template used in a create resource.
;; NOTE: The name must match the key defined by the resource, :userTemplate here.
(s/def :cimi.user-params-template.exec/userParamTemplate
  (su/only-keys-maps ps/template-keys-spec
                     user-params-template-exec-keys-spec))

(s/def :cimi/user-params-template.exec-create
  (su/only-keys-maps ps/create-keys-spec
                     {:opt-un [:cimi.user-params-template.exec/userParamTemplate]}))

