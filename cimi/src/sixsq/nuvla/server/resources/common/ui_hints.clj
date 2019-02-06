(ns sixsq.nuvla.server.resources.common.ui-hints)


(def UIHintsParameterDescription
  {:group       {:displayName "Group"
                 :category    "general"
                 :description "optional label for grouping related templates"
                 :type        "string"
                 :mandatory   false
                 :readOnly    true
                 :order       90}
   :order       {:displayName "Order"
                 :category    "general"
                 :description "optional visualization order for related templates"
                 :type        "string"
                 :mandatory   false
                 :readOnly    true
                 :order       91}
   :hidden      {:displayName "Hidden"
                 :category    "general"
                 :description "optional flag to indicate that template should be hidden"
                 :type        "boolean"
                 :mandatory   false
                 :readOnly    true
                 :order       92}
   :icon        {:displayName "Icon"
                 :category    "general"
                 :description "name of optional icon to associated with a template"
                 :type        "boolean"
                 :mandatory   false
                 :readOnly    true
                 :order       93}
   :redirectURI {:displayName "Redirect URI"
                 :category    "general"
                 :description "optional redirect URI to be used on success"
                 :type        "hidden"
                 :mandatory   false
                 :readOnly    false
                 :order       94}})
