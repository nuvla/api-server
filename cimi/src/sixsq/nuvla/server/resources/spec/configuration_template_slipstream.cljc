(ns sixsq.nuvla.server.resources.spec.configuration-template-slipstream
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.configuration-template :as ps]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::slipstreamVersion ::cimi-core/nonblank-string)
(s/def ::serviceURL ::cimi-core/nonblank-string)
(s/def ::reportsLocation ::cimi-core/nonblank-string)
(s/def ::supportEmail ::cimi-core/nonblank-string)
(s/def ::clientBootstrapURL ::cimi-core/nonblank-string)
(s/def ::clientURL ::cimi-core/nonblank-string)
(s/def ::connectorOrchPrivateSSHKey ::cimi-core/nonblank-string)
(s/def ::connectorOrchPublicSSHKey ::cimi-core/nonblank-string)
(s/def ::connectorLibcloudURL ::cimi-core/nonblank-string)

(s/def ::mailUsername ::cimi-core/nonblank-string)
(s/def ::mailPassword ::cimi-core/nonblank-string)
(s/def ::mailHost ::cimi-core/nonblank-string)
(s/def ::mailPort ::cimi-core/port)
(s/def ::mailSSL boolean?)
(s/def ::mailDebug boolean?)
(s/def ::termsAndConditions ::cimi-core/nonblank-string)

(s/def ::quotaEnable boolean?)

(s/def ::registrationEnable boolean?)
(s/def ::registrationEmail ::cimi-core/nonblank-string)

(s/def ::meteringEnable boolean?)
(s/def ::meteringEndpoint ::cimi-core/nonblank-string)

(s/def ::serviceCatalogEnable boolean?)

;; FIXME: used only for compatibilty with the Java server. To be removed.
(s/def ::cloudConnectorClass string?)

(s/def ::metricsLoggerEnable boolean?)
(s/def ::metricsGraphiteEnable boolean?)

(s/def ::reportsObjectStoreBucketName string?)
(s/def ::reportsObjectStoreCreds string?)

(def configuration-template-keys-spec-req
  {:req-un [::slipstreamVersion
            ::serviceURL
            ::supportEmail
            ::clientBootstrapURL
            ::clientURL
            ::connectorOrchPrivateSSHKey
            ::connectorOrchPublicSSHKey
            ::connectorLibcloudURL

            ::mailUsername
            ::mailPassword
            ::mailHost
            ::mailPort
            ::mailSSL
            ::mailDebug

            ::quotaEnable

            ::registrationEnable
            ::registrationEmail

            ::meteringEnable
            ::meteringEndpoint

            ::serviceCatalogEnable

            ::cloudConnectorClass

            ::metricsLoggerEnable
            ::metricsGraphiteEnable
            ::reportsObjectStoreBucketName
            ::reportsObjectStoreCreds]
   :opt-un [::reportsLocation ; reportsLocation is deprecated
            ::termsAndConditions]})

;; FIXME: Treats all parameters as optional.  Instead those without reasonable defaults should be required.
(def configuration-template-keys-spec-opt
  {:opt-un (concat (:req-un configuration-template-keys-spec-req)
                   (:opt-un configuration-template-keys-spec-req))})

;; Defines the contents of the slipstream ConfigurationTemplate resource itself.
(s/def ::slipstream
  (su/only-keys-maps ps/resource-keys-spec
                     configuration-template-keys-spec-req))

;; Defines the contents of the slipstream template used in a create resource.
;; NOTE: The name must match the key defined by the resource, :configurationTemplate here.
(s/def ::configurationTemplate
  (su/only-keys-maps ps/template-keys-spec
                     configuration-template-keys-spec-opt))

(s/def ::slipstream-create
  (su/only-keys-maps ps/create-keys-spec
                     {:opt-un [::configurationTemplate]}))
