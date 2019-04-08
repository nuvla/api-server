(ns sixsq.nuvla.server.resources.spec.common-operation
  "Spec definitions for common operation types used in CIMI resources."
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [spec-tools.core :as st]))


(s/def ::href
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "href"
             :json-schema/name "href"
             :json-schema/type "string"
             :json-schema/editable false

             :json-schema/display-name "href"
             :json-schema/description "URI for operation")))


(s/def ::rel
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "rel"
             :json-schema/name "rel"
             :json-schema/type "string"
             :json-schema/editable false

             :json-schema/display-name "rel"
             :json-schema/description "URL for performing action")))



