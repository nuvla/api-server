(ns sixsq.nuvla.server.app.routes
  (:require
    [compojure.core :refer [ANY DELETE GET let-routes OPTIONS PATCH POST PUT routes]]
    [compojure.route :as route]
    [ring.middleware.head :refer [wrap-head]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.dynamic-load :as dyn]
    [sixsq.nuvla.server.util.response :as r]))


(def collection-routes
  (let-routes [uri (str p/service-context ":resource-name")]
    (POST uri request
      (crud/add request))
    (PUT uri request
      (crud/query request))
    (GET uri request
      (crud/query request))
    (DELETE uri request
      (crud/bulk-delete request))
    (ANY uri request
      (throw (r/ex-bad-method request)))))


(def resource-routes
  (let-routes [uri (str p/service-context ":resource-name/:uuid")]
    (GET uri request
      (crud/retrieve request))
    (PUT uri request
      (crud/edit request))
    (DELETE uri request
      (crud/delete request))
    (ANY uri request
      (throw (r/ex-bad-method request)))))


(def action-routes
  (let-routes [uri (str p/service-context ":resource-name/:uuid/:action")]
    (ANY uri request
      (crud/do-action request))))


(def bulk-action-routes
  (let-routes [uri (str p/service-context ":resource-name/:action")]
    (PATCH uri request
      (crud/bulk-action request))))


(defn not-found
  "Route always returns a 404 error response as a JSON map."
  []
  (wrap-head
    (fn [{:keys [uri]}]
      (r/map-response "unknown resource" 404 uri))))


(def final-routes
  [collection-routes
   bulk-action-routes
   resource-routes
   action-routes
   (not-found)])

(def cors-preflight-check-route
  (OPTIONS "*" []
    (r/map-response "preflight complete" 204)))


(defn get-main-routes
  "Returns all of the routes defined for the server.  This uses
   dynamic loading to discover all of the defined resources on the
   classpath."
  []
  (apply routes (doall (concat [cors-preflight-check-route
                                (route/resources (str p/service-context "static"))]
                               (dyn/resource-routes)
                               final-routes))))
