(ns sixsq.nuvla.server.resources.credential.utils
  "General utilities for dealing with credential resources and collection."
  (:require
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as credential]))

(defn all-registry-creds-exist
  [creds request]
  (and (seq creds)
       (< (crud/query-count credential/resource-type
                         (str "subtype='infrastructure-service-registry' and "
                              (u/filter-eq-vals "id" creds))
                         request)
          (count creds))))
