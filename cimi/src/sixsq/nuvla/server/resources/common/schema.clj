(ns sixsq.nuvla.server.resources.common.schema)

(def ^:const slipstream-schema-uri "http://sixsq.com/slipstream/1/")

;; using draft 2.0.0c
(def ^:const cimi-schema-uri "http://schemas.dmtf.org/cimi/2/")

;;
;; actions
;;

(def ^:const actions
  #{:add, :edit, :delete, :start, :stop, :restart, :pause, :suspend,
    :snapshot, :enable, :disable, :validate, :collect, :execute,
    :activate, :quarantine, :upload, :ready, :download})

(def ^:const action-uri
  (doall
    (into {} (map (juxt identity name) actions))))
