(ns com.sixsq.nuvla.server.app.server
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.db.loader :as db-loader]
    [com.sixsq.nuvla.server.app.routes :as routes]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [wrap-authn-info]]
    [com.sixsq.nuvla.server.middleware.base-uri :refer [wrap-base-uri]]
    [com.sixsq.nuvla.server.middleware.cimi-params :refer [wrap-cimi-params]]
    [com.sixsq.nuvla.server.middleware.default-content-type :refer [default-content-type]]
    [com.sixsq.nuvla.server.middleware.eventer :refer [wrap-eventer]]
    [com.sixsq.nuvla.server.middleware.exception-handler :refer [wrap-exceptions]]
    [com.sixsq.nuvla.server.middleware.gzip :refer [wrap-gzip-uncompress]]
    [com.sixsq.nuvla.server.middleware.logger :refer [wrap-logger]]
    [com.sixsq.nuvla.server.middleware.redirect-cep :refer [redirect-cep]]
    [com.sixsq.nuvla.server.resources.common.dynamic-load :as resources]
    [com.sixsq.nuvla.server.util.kafka :as kafka]
    [com.sixsq.nuvla.server.util.zookeeper :as zku]
    [compojure.core :as compojure]
    [environ.core :as env]
    [jsonista.core :as j]
    [nrepl.server :as nrepl]
    [nrepl.transport :as transport]
    [ring.middleware.cookies :refer [wrap-cookies]]
    [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
    [ring.middleware.keyword-params :refer [wrap-keyword-params]]
    [ring.middleware.nested-params :refer [wrap-nested-params]]
    [ring.middleware.params :refer [wrap-params]]
    [taoensso.telemere :as telemere]))


(def default-db-binding-ns "com.sixsq.nuvla.db.es.loader")

(defonce server (when-let [nrepl-port (some-> (env/env :nrepl-port) Integer/parseInt)]
                  (log/warn "Starting nREPL on port" nrepl-port)
                  (nrepl/start-server :port nrepl-port :bind "0.0.0.0"
                                      :greeting-fn (fn [transport]
                                                     (transport/send transport {:out (str ";; Hello, you are connected to Nuvla API-SERVER ;)"
                                                                                          \newline)})))))


(defn- create-ring-handler
  "Creates a ring handler that wraps all the service routes
   in the necessary ring middleware to handle authentication,
   header treatment, and message formatting."
  []
  (log/info "creating ring handler")

  (compojure/routes)

  (-> (routes/get-main-routes)

      ;; handler/site
      wrap-cimi-params
      wrap-base-uri
      wrap-keyword-params
      wrap-nested-params
      wrap-params
      wrap-exceptions
      (wrap-json-body {:keywords? true})
      wrap-gzip-uncompress
      wrap-eventer
      wrap-authn-info
      (wrap-json-response {:pretty           true
                           :escape-non-ascii true})
      (default-content-type "application/json")
      redirect-cep
      wrap-logger
      wrap-cookies))


(defn stop
  "Stops the application server by calling the function that was
   created when the application server was started."
  []

  (try
    (kafka/close-producers!)
    (log/info "kafka producers closed")
    (catch Exception e
      (log/warn "kafka producers close failed:" (str e))))

  (try
    (zku/close-client!)
    (log/info "zookeeper client closed")
    (catch Exception e
      (log/warn "zookeeper client close failed:" (str e))))

  (db/close)

  (try
    (log/info "removed all instrumentation metrics")
    (catch Exception e
      (log/warn "failed removing all instrumentation metrics:" (str e)))))

(defn init-logging
  []
  (when (telemere/get-env {:as :edn :default false} [:json-logging :json-logging<.edn>])
    (telemere/add-handler! ::log-json-handler (telemere/handler:console
                                                {:output-fn
                                                 (telemere/pr-signal-fn
                                                   {:pr-fn j/write-value-as-string})}))
    (telemere/remove-handler! :default/console)
    (telemere/log! :info "Logging in json enabled.")))

(defn init
  []
  (init-logging)

  (db-loader/load-and-set-persistent-db-binding
    (env/env :persistent-db-binding-ns default-db-binding-ns))

  (try
    (zku/set-client! (zku/create-client))
    (catch Exception e
      (log/error "error creating zookeeper client:" (str e))
      (throw e)))

  (try
    (kafka/create-producers!)
    (catch Exception e
      (log/error "error creating kafka producer:" (str e))
      (throw e)))

  (try
    (resources/initialize)
    (catch Exception e
      (log/error "error initializing resources:" (str e))
      (throw e)))

  ;; returns tuple with handler and stop function
  [(create-ring-handler) stop])


(comment
  ;; start the server
  (do
    (require '[com.sixsq.nuvla.server.ring :as ring])
    (defonce server-stop-fn (atom nil))
    (if @server-stop-fn
      (println "Server already started")
      (do
        (print "Starting the server..")
        (let [stop-fn (ring/start 'com.sixsq.nuvla.server.app.server/init)]
          (reset! server-stop-fn stop-fn)
          (println "..started")))))

  ;; stop the server
  (if-let [stop @server-stop-fn]
    (do
      (print "Stopping the server..")
      (stop)
      (reset! server-stop-fn nil)
      (println "stopped"))
    (println "Server not started")))
