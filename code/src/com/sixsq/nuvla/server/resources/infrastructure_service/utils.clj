(ns com.sixsq.nuvla.server.resources.infrastructure-service.utils
  "General utilities for dealing with infrastructure-service resources and collection."
  (:require
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.infrastructure-service :as infra-service]))

(def subtype-registry "registry")
(def subtype-helm-repo "helm-repo")

(defn missing-infra?
  [subtype registries request]
  (and (seq registries)
       (< (crud/query-count infra-service/resource-type
                            (str "subtype='" subtype "' and "
                                 (u/filter-eq-vals "id" registries))
                            request)
          (count registries))))

(defn missing-registries?
  [registries request]
  (missing-infra? subtype-registry registries request))

(defn missing-helm-repo-url?
  [helm-repo-url request]
  (missing-infra? subtype-helm-repo [helm-repo-url] request))
