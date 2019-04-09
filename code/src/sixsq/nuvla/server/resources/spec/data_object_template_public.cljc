(ns sixsq.nuvla.server.resources.spec.data-object-template-public
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.data-object :as do]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

(def template-resource-keys-spec
  (u/remove-req do/common-data-object-attrs #{::do/state}))

;; Defines the contents of the public template used in a create resource.
(s/def ::template
  (-> (st/spec (su/only-keys-maps c/template-attrs
                                  template-resource-keys-spec))
      (assoc :name "template"
             :json-schema/type "map")))

(s/def ::data-object-create
  (su/only-keys-maps c/create-attrs
                     {:req-un [::template]}))
