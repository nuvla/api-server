(ns sixsq.nuvla.server.resources.spec.ui-hints
  "Attributes that can be used to provide visualization hints for browser (or
   other visual) user interfaces."
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [spec-tools.core :as st]))


(s/def ::group
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "group"
             :json-schema/name "group"
             :json-schema/type "string"
             :json-schema/required false
             :json-schema/editable true

             :json-schema/display-name "group"
             :json-schema/description "label for grouping related templates/forms"
             :json-schema/group "body"
             :json-schema/order 60
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::order
  (-> (st/spec nat-int?)
      (assoc :name "order"
             :json-schema/name "order"
             :json-schema/type "integer"
             :json-schema/required false
             :json-schema/editable true

             :json-schema/display-name "order"
             :json-schema/description "hint for visualization order for field"
             :json-schema/group "body"
             :json-schema/order 61
             :json-schema/hidden false
             :json-schema/sensitive false

             :json-schema/value-scope {:minimum 0
                                       :default 0})))


(s/def ::hidden
  (-> (st/spec boolean?)
      (assoc :name "hidden"
             :json-schema/name "hidden"
             :json-schema/type "boolean"
             :json-schema/required false
             :json-schema/editable true

             :json-schema/display-name "hidden"
             :json-schema/description "should template be hidden on browser UIs"
             :json-schema/group "body"
             :json-schema/order 62
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::icon
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "icon"
             :json-schema/name "icon"
             :json-schema/type "string"
             :json-schema/required false
             :json-schema/editable true

             :json-schema/display-name "icon"
             :json-schema/description "name for icon to associate to template"
             :json-schema/group "body"
             :json-schema/order 63
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::redirect-url
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "redirect-url"
             :json-schema/name "redirect-url"
             :json-schema/type "string"
             :json-schema/required false
             :json-schema/editable true

             :json-schema/display-name "redirect-url"
             :json-schema/description "redirect URI to be used on success"
             :json-schema/group "body"
             :json-schema/order 64
             :json-schema/hidden false
             :json-schema/sensitive false)))


(def ui-hints-spec {:opt-un [::group ::order ::hidden ::icon ::redirect-url]})
