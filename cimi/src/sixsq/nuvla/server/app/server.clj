(ns sixsq.nuvla.server.app.server
  (:require
    [clojure.tools.logging :as log]
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
    [sixsq.nuvla.server.middleware.authn-info-header :refer [wrap-authn-info-header]]
    [sixsq.nuvla.server.middleware.base-uri :refer [wrap-base-uri]]
    [sixsq.nuvla.server.middleware.cimi-params :refer [wrap-cimi-params]]
    [sixsq.nuvla.server.middleware.exception-handler :refer [wrap-exceptions]]
    [sixsq.nuvla.server.middleware.logger :refer [wrap-logger]]
    [sixsq.nuvla.server.resources.common.dynamic-load :as resources]
    [sixsq.nuvla.server.util.zookeeper :as zku]))


(def default-db-binding-ns "sixsq.nuvla.db.es.loader")


(defn- create-ring-handler
  "Creates a ring handler that wraps all of the service routes
   in the necessary ring middleware to handle authentication,
   header treatment, and message formatting."
  []
  (log/info "creating ring handler")

  (compojure.core/routes)

  (-> (routes/get-main-routes)

      ;; handler/site
      wrap-cimi-params
      wrap-base-uri
      wrap-keyword-params
      wrap-nested-params
      wrap-params
      wrap-exceptions
      wrap-authn-info-header
      (wrap-json-body {:keywords? true})
      (wrap-json-response {:pretty true :escape-non-ascii true})
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

  (try
    (catch Exception e
      (log/warn "error registering instrumentation metrics:" (str e))))

  (db-loader/load-and-set-persistent-db-binding
    (env/env :persistent-db-binding-ns default-db-binding-ns))

  (db-loader/load-and-set-ephemeral-db-binding
    (env/env :ephemeral-db-binding-ns))

  (try
    (zku/set-client! (zku/create-client))
    (catch Exception e
      (log/warn "error creating zookeeper client:" (str e))))

  (try
    (resources/initialize)
    (catch Exception e
      (log/warn "error initializing resources:" (str e))))

  ;; returns tuple with handler and stop function
  [(create-ring-handler) stop])
