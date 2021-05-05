(ns sixsq.nuvla.server.app.server
  (:require
    [clojure.tools.logging :as log]
    [compojure.core :as compojure]
    [environ.core :as env]
    [ring.middleware.cookies :refer [wrap-cookies]]
    [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
    [ring.middleware.keyword-params :refer [wrap-keyword-params]]
    [ring.middleware.nested-params :refer [wrap-nested-params]]
    [ring.middleware.params :refer [wrap-params]]
    [sixsq.nuvla.db.ephemeral-impl :as edb]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.db.loader :as db-loader]
    [sixsq.nuvla.server.app.routes :as routes]
    [sixsq.nuvla.server.middleware.authn-info :refer [wrap-authn-info]]
    [sixsq.nuvla.server.middleware.base-uri :refer [wrap-base-uri]]
    [sixsq.nuvla.server.middleware.cimi-params :refer [wrap-cimi-params]]
    [sixsq.nuvla.server.middleware.default-content-type :refer [default-content-type]]
    [sixsq.nuvla.server.middleware.exception-handler :refer [wrap-exceptions]]
    [sixsq.nuvla.server.middleware.logger :refer [wrap-logger]]
    [sixsq.nuvla.server.middleware.redirect-cep :refer [redirect-cep]]
    [sixsq.nuvla.server.resources.common.dynamic-load :as resources]
    [sixsq.nuvla.server.util.kafka :as kafka]
    [sixsq.nuvla.server.util.zookeeper :as zku]))


(def default-db-binding-ns "sixsq.nuvla.db.es.loader")


(defn- create-ring-handler
  "Creates a ring handler that wraps all of the service routes
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
      wrap-authn-info
      (wrap-json-body {:keywords? true})
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
    (zku/close-client!)
    (log/info "zookeeper client closed")
    (catch Exception e
      (log/warn "zookeeper client close failed:" (str e))))

  (db/close)

  (edb/close)

  (try
    (log/info "removed all instrumentation metrics")
    (catch Exception e
      (log/warn "failed removing all instrumentation metrics:" (str e)))))


(defn init
  []

  (db-loader/load-and-set-persistent-db-binding
    (env/env :persistent-db-binding-ns default-db-binding-ns))

  (db-loader/load-and-set-ephemeral-db-binding
    (env/env :ephemeral-db-binding-ns))

  (try
    (zku/set-client! (zku/create-client))
    (catch Exception e
      (log/error "error creating zookeeper client:" (str e))
      (throw e)))

  (try
    (kafka/set-producer! (kafka/create-producer))
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
