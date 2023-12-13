(ns sixsq.nuvla.server.resources.infrastructure-service.utils
  "General utilities for dealing with infrastructure-service resources and collection."
  (:require
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]))

(defn all-registries-exist
  [registries request]
  (and (seq registries)
       (< (crud/query-count infra-service/resource-type
                         (str "subtype='registry' and "
                              (u/filter-eq-vals "id" registries))
                         request)
          (count registries))))
