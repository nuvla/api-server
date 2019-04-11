(ns sixsq.nuvla.server.resources.spec.data-object-public
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.data-object :as do]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::url
  (-> (st/spec ::core/url)
      (assoc :name "url"
             :json-schema/description "public access URL for object"

             :json-schema/server-managed true
             :json-schema/editable false
             :json-schema/order 40)))


(def data-object-public-keys-spec
  (su/merge-keys-specs [do/common-data-object-attrs
                        {:opt-un [::url]}]))


(def resource-keys-spec
  (su/merge-keys-specs [common/common-attrs
                        data-object-public-keys-spec]))


(s/def ::schema
  (su/only-keys-maps resource-keys-spec))
