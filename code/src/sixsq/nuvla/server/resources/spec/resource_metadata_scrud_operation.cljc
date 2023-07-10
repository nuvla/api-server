(ns sixsq.nuvla.server.resources.spec.resource-metadata-scrud-operation
  "schema definitions for the 'scrud-operations' field of a ResourceMetadata resource"
  (:require
    [clojure.spec.alpha :as s]))


(s/def ::scrud-operation #{;; resource level operations
                           :get :edit :delete
                           ;; collection level operations
                           :add :query :bulk-delete})


(s/def ::scrud-operations
  (s/spec (s/coll-of ::scrud-operation :min-count 1 :type vector?)))
