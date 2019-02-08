(ns sixsq.nuvla.server.resources.common.schema)

;;
;; actions
;;

(def ^:const actions
  #{:add, :edit, :change, :delete, :start, :stop, :restart, :pause, :suspend,
    :snapshot, :enable, :disable, :validate, :collect, :execute,
    :activate, :quarantine, :upload, :ready, :download})

(def ^:const action-uri
  (doall
    (into {} (map (juxt identity name) actions))))
