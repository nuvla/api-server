(ns sixsq.nuvla.server.resources.common.schema)

;;
;; actions
;;

(def ^:const actions
  #{:add :edit :change :delete :start :stop :restart :pause :suspend
    :snapshot :enable :disable :validate :collect :execute
    :activate :quarantine :recommission
    :upload :ready :download :redeem :expire
    :check-password :change-password :defer})

(def ^:const action-uri
  (doall
    (into {} (map (juxt identity name) actions))))
