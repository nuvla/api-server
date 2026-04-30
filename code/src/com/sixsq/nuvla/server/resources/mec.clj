(ns com.sixsq.nuvla.server.resources.mec
  "Top-level route mounting for MEC-specific APIs."
  (:require
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.resources.mec.app-lcm-v2 :as app-lcm-v2]
    [com.sixsq.nuvla.server.resources.mec.app-package :as app-package]
    [com.sixsq.nuvla.server.util.response :as r]
    [compojure.core :refer [ANY DELETE GET POST PUT defroutes let-routes]]))

(def ^:private base-uri (str p/service-context "mec/app_lcm/v2"))

(defn- with-full-id
  [request target-key]
  (let [{:keys [resourceName uuid]} (:params request)]
    (assoc-in request [:params target-key] (str resourceName "/" uuid))))

(defroutes routes
  ;; Mm1 application package management
  (GET (str base-uri "/app_packages") request
    (app-package/query-app-packages request))
  (POST (str base-uri "/app_packages") request
    (app-package/create-app-package request))
  (let-routes [uri (str base-uri "/app_packages/:resourceName/:uuid")]
    (GET uri request
      (app-package/get-app-package (with-full-id request :appPkgId)))
    (DELETE uri request
      (app-package/delete-app-package (with-full-id request :appPkgId))))
  (let-routes [uri (str base-uri "/app_packages/:resourceName/:uuid/appD")]
    (GET uri request
      (app-package/get-app-package-appd (with-full-id request :appPkgId))))
  (let-routes [uri (str base-uri "/app_packages/:resourceName/:uuid/package_content")]
    (GET uri request
      (app-package/get-app-package-content (with-full-id request :appPkgId)))
    (PUT uri request
      (app-package/put-app-package-content (with-full-id request :appPkgId))))

  ;; Mm1 application lifecycle management
  (GET (str base-uri "/app_instances") request
    (app-lcm-v2/list-app-instances-handler request))
  (POST (str base-uri "/app_instances") request
    (app-lcm-v2/create-app-instance-handler request))
  (let-routes [uri (str base-uri "/app_instances/:resourceName/:uuid")]
    (GET uri request
      (app-lcm-v2/get-app-instance-handler (with-full-id request :id)))
    (DELETE uri request
      (app-lcm-v2/delete-app-instance-handler (with-full-id request :id))))
  (let-routes [uri (str base-uri "/app_instances/:resourceName/:uuid/instantiate")]
    (POST uri request
      (app-lcm-v2/instantiate-app-instance-handler (with-full-id request :id))))
  (let-routes [uri (str base-uri "/app_instances/:resourceName/:uuid/terminate")]
    (POST uri request
      (app-lcm-v2/terminate-app-instance-handler (with-full-id request :id))))
  (let-routes [uri (str base-uri "/app_instances/:resourceName/:uuid/operate")]
    (POST uri request
      (app-lcm-v2/operate-app-instance-handler (with-full-id request :id))))
  (GET (str base-uri "/app_lcm_op_occs") request
    (app-lcm-v2/list-app-lcm-op-occs-handler request))
  (let-routes [uri (str base-uri "/app_lcm_op_occs/:resourceName/:uuid")]
    (GET uri request
      (app-lcm-v2/get-app-lcm-op-occ-handler (with-full-id request :id))))
  (GET (str base-uri "/subscriptions") request
    (app-lcm-v2/list-subscriptions-handler request))
  (POST (str base-uri "/subscriptions") request
    (app-lcm-v2/create-subscription-handler request))
  (let-routes [uri (str base-uri "/subscriptions/:resourceName/:uuid")]
    (GET uri request
      (app-lcm-v2/get-subscription-handler (with-full-id request :id)))
    (DELETE uri request
      (app-lcm-v2/delete-subscription-handler (with-full-id request :id))))

  (ANY (str base-uri "*") request
    (throw (r/ex-bad-method request))))
