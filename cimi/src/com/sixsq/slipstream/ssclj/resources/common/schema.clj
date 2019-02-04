(ns com.sixsq.slipstream.ssclj.resources.common.schema)

(def ^:const slipstream-schema-uri "http://sixsq.com/slipstream/1/")

;; using draft 2.0.0c
(def ^:const cimi-schema-uri "http://schemas.dmtf.org/cimi/2/")

;;
;; actions
;;

;; core actions do not have a URI prefix
(def ^:const core-actions
  #{:add :edit :delete :insert :remove})

;; additional resource actions have a URI prefix
(def ^:const action-prefix (str cimi-schema-uri "action/"))
(def ^:const prefixed-actions
  #{:start :stop :restart :pause :suspend
    :export :import :capture :snapshot
    :forceSync :swapBackup :restore :enable :disable})

;; implementation-specific resource actions have a prefix
(def ^:const impl-action-prefix (str slipstream-schema-uri "action/"))
(def ^:const impl-prefixed-actions
  #{:describe
    :validate
    :collect
    :execute
    :activate
    :quarantine
    :upload
    :ready
    :download})

(def ^:const action-uri
  (doall
    (merge
      (into {} (map (juxt identity name) core-actions))
      (into {} (map (juxt identity #(str action-prefix (name %))) prefixed-actions))
      (into {} (map (juxt identity #(str impl-action-prefix (name %))) impl-prefixed-actions)))))

(def CommonParameterDescription
  {:id          {:displayName "ID"
                 :category    "common"
                 :description "unique resource identifier"
                 :type        "string"
                 :mandatory   true
                 :readOnly    true
                 :order       0}
   :resourceURI {:displayName "Resource URI"
                 :category    "common"
                 :description "type identifier as a URI"
                 :type        "string"
                 :mandatory   true
                 :readOnly    true
                 :order       1}
   :name        {:displayName "Name"
                 :category    "common"
                 :description "human-readable name"
                 :type        "string"
                 :mandatory   false
                 :readOnly    false
                 :order       2}
   :description {:displayName "Description"
                 :category    "common"
                 :description "short, human-readable description"
                 :type        "string"
                 :mandatory   false
                 :readOnly    false
                 :order       3}
   :created     {:displayName "Created"
                 :category    "common"
                 :description "creation timestamp"
                 :type        "timestamp"
                 :mandatory   true
                 :readOnly    true
                 :order       4}
   :updated     {:displayName "Updated"
                 :category    "common"
                 :description "update timestamp"
                 :type        "timestamp"
                 :mandatory   true
                 :readOnly    true
                 :order       5}
   :properties  {:displayName "Properties"
                 :category    "common"
                 :description "user-defined properties"
                 :type        "map"
                 :mandatory   false
                 :readOnly    false
                 :order       6}
   :operations  {:displayName "Operation"
                 :category    "common"
                 :description "allowed actions"
                 :type        "list"
                 :mandatory   false
                 :readOnly    true
                 :order       7}})

