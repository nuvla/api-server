(ns sixsq.nuvla.server.resources.configuration-template-nuvla
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-template :as p]
    [sixsq.nuvla.server.resources.spec.configuration-template-nuvla :as configuration-template]))


(def ^:const service "slipstream")


;;
;; resource
;;

(def ^:const resource
  {
   :service                      service
   :name                         "SlipStream"
   :description                  "SlipStream Service Configuration"
   :serviceURL                   "https://localhost"
   :supportEmail                 "support@example.com"
   :clientBootstrapURL           "https://localhost/downloads/slipstream.bootstrap"
   :clientURL                    "https://localhost/downloads/slipstreamclient.tgz"
   :connectorOrchPrivateSSHKey   "/opt/slipstream/server/.ssh/id_rsa"
   :connectorOrchPublicSSHKey    "/opt/slipstream/server/.ssh/id_rsa.pub"
   :connectorLibcloudURL         "https://localhost/downloads/libcloud.tgz"

   :mailUsername                 "mailer"
   :mailPassword                 "change-me"
   :mailHost                     "smtp.example.com"
   :mailPort                     465
   :mailSSL                      true
   :mailDebug                    true

   ;; Optional, without a good default.
   ;;:termsAndConditions           "https://example.com/terms/update-or-remove"

   :quotaEnable                  true

   :registrationEnable           true
   :registrationEmail            "register@example.com"

   :meteringEnable               false
   :meteringEndpoint             "http://localhost:2005"

   :serviceCatalogEnable         false

   :slipstreamVersion            "UNKNOWN"

   :cloudConnectorClass          ""

   :metricsLoggerEnable          false
   :metricsGraphiteEnable        false

   :reportsObjectStoreBucketName "slipstream-reports"
   :reportsObjectStoreCreds      "credential/<CHANGE-ME-UUID>"})


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::configuration-template/slipstream))
(defmethod p/validate-subtype service
  [resource]
  (validate-fn resource))


;;
;; initialization: register this Configuration template
;;

(defn initialize
  []
  (p/register resource))
