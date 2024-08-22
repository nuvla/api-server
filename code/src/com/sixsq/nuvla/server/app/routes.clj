(ns com.sixsq.nuvla.server.app.routes
  (:require
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.dynamic-load :as dyn]
    [com.sixsq.nuvla.server.resources.common.event-context :as ec]
    [com.sixsq.nuvla.server.util.response :as r]
    [compojure.core :refer [ANY DELETE GET let-routes OPTIONS PATCH POST PUT routes]]
    [compojure.route :as route]
    [ring.middleware.head :refer [wrap-head]]))


(def collection-routes
  (let-routes [uri (str p/service-context ":resource-name")]
    (POST uri request
      (ec/add-to-context :params (:params request))
      (ec/add-to-context :category "add")
      (crud/add request))
    (PUT uri request
      (ec/add-to-context :params (:params request))
      (crud/query request))
    (GET uri request
      (ec/add-to-context :params (:params request))
      (crud/query request))
    (DELETE uri request
      (ec/add-to-context :params (:params request))
      (crud/bulk-delete request))
    (ANY uri request
      (throw (r/ex-bad-method request)))))


(def resource-routes
  (let-routes [uri (str p/service-context ":resource-name/:uuid")]
    (GET uri request
      (ec/add-to-context :params (:params request))
      (crud/retrieve request))
    (PUT uri request
      (ec/add-to-context :params (:params request))
      (ec/add-to-context :category "edit")
      (crud/edit (assoc-in request [:params :action] crud/action-edit)))
    (DELETE uri request
      (ec/add-to-context :params (:params request))
      (ec/add-to-context :category "delete")
      (crud/delete (assoc-in request [:params :action] crud/action-delete)))
    (ANY uri request
      (throw (r/ex-bad-method request)))))


(def action-routes
  (let-routes [uri (str p/service-context ":resource-name/:uuid/:action")]
    (ANY uri request
      (ec/add-to-context :params (:params request))
      (ec/add-to-context :category "action")
      (crud/do-action request))))


(def bulk-action-routes
  (let-routes [uri (str p/service-context ":resource-name/:action")]
    (PATCH uri request
      (ec/add-to-context :params (:params request))
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
