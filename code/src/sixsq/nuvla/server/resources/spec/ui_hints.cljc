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
             :json-schema/display-name "group"
             :json-schema/description "label for grouping related templates/forms"

             :json-schema/order 60)))


(s/def ::order
  (-> (st/spec nat-int?)
      (assoc :name "order"
             :json-schema/name "order"
             :json-schema/type "integer"
             :json-schema/display-name "order"
             :json-schema/description "hint for visualization order for field"

             :json-schema/order 61

             :json-schema/value-scope {:minimum 0
                                       :default 0})))


(s/def ::hidden
  (-> (st/spec boolean?)
      (assoc :name "hidden"
             :json-schema/name "hidden"
             :json-schema/type "boolean"
             :json-schema/display-name "hidden"
             :json-schema/description "should template be hidden on browser UIs"

             :json-schema/order 62)))


(s/def ::icon
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "icon"
             :json-schema/name "icon"
             :json-schema/display-name "icon"
             :json-schema/description "name for icon to associate to template"

             :json-schema/order 63)))


(s/def ::redirect-url
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "redirect-url"
             :json-schema/name "redirect-url"
             :json-schema/display-name "redirect-url"
             :json-schema/description "redirect URI to be used on success"
             
             :json-schema/order 64)))


(def ui-hints-spec {:opt-un [::group ::order ::hidden ::icon ::redirect-url]})
