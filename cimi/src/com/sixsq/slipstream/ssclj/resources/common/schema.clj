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
  #{:validate
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
