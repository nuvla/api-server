(ns sixsq.nuvla.server.resources.spec.data-object-template-public
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.data-object :as do]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(def template-resource-keys-spec do/common-data-object-tpl-attrs)


;; Defines the contents of the public template used in a create resource.
(s/def ::template
  (-> (st/spec (su/only-keys-maps common/template-attrs
                                  template-resource-keys-spec))
      (assoc :name "template"
             :json-schema/type "map")))


(s/def ::schema-create
  (su/only-keys-maps common/create-attrs
                     {:req-un [::template]}))
