(ns sixsq.nuvla.server.resources.spec.configuration
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.configuration-template :as ps]
    [sixsq.nuvla.server.resources.spec.configuration-template-nuvla :as nuvla]
    [sixsq.nuvla.server.resources.spec.configuration-template-session-github :as session-github]
    [sixsq.nuvla.server.resources.spec.configuration-template-session-mitreid :as session-mitreid]
    [sixsq.nuvla.server.resources.spec.configuration-template-session-mitreid-token
     :as session-mitreid-token]
    [sixsq.nuvla.server.resources.spec.configuration-template-session-oidc :as session-oidc]
    [sixsq.nuvla.server.resources.spec.configuration-template-vpn-api :as vpn-api]
    [sixsq.nuvla.server.util.spec :as su]))

;;
;; Note that all of the keys and keys specs are already defined
;; with the ConfigurationTemplate.  This file exists only to
;; list all attributes metadata.
;;

(s/def ::schema (su/only-keys-maps
                  ps/resource-keys-spec
                  nuvla/configuration-template-keys-spec
                  session-github/configuration-template-keys-spec-create
                  session-mitreid/configuration-template-keys-spec-req
                  session-mitreid-token/configuration-template-keys-spec-req
                  session-oidc/configuration-template-keys-spec-req
                  vpn-api/configuration-template-keys-spec-req))
