(ns sixsq.nuvla.server.resources.spec.common-body
  "Spec definitions for common request body types."
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

(s/def ::doc
  (-> (st/spec (su/only-keys-maps {:req-un [::common/tags]}))
      (assoc :name "doc"
             :json-schema/type "map"
             :json-schema/display-name "doc")))

(s/def ::filter
  (-> (st/spec string?)
      (assoc :name "filter"
             :json-schema/type "string"
             :json-schema/display-name "filter")))
(s/def ::bulk-edit-tags-body (su/only-keys-maps {:req-un [::doc]
                                                 :opt-un [::filter]}))
