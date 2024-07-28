(ns sixsq.nuvla.server.resources.credential.utils
  "General utilities for dealing with credential resources and collection."
  (:require
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-helm-repo :as ctishr]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-registry :as ctisr]))

(defn missing-creds?
  [subtype creds request]
  (and (seq creds)
       (< (crud/query-count credential/resource-type
                            (str "subtype='" subtype "' and "
                                 (u/filter-eq-vals "id" creds))
                            request)
          (count creds))))

(defn missing-registries-creds?
  [creds request]
  (missing-creds? ctisr/credential-subtype creds request))

(defn missing-helm-repo-cred?
  [cred request]
  (missing-creds? ctishr/credential-subtype [cred] request))
