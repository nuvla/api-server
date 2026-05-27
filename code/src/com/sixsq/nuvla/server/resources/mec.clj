(ns com.sixsq.nuvla.server.resources.mec
  "Top-level route mounting for MEC-specific APIs."
  (:require
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.resources.mec.app-lcm-subscription :as app-lcm-subscription]
    [com.sixsq.nuvla.server.resources.mec.app-lcm :as app-lcm]
    [com.sixsq.nuvla.server.resources.mec.app-package :as app-package]
    [com.sixsq.nuvla.server.resources.mec.app-package-subscription :as app-package-subscription]
    [com.sixsq.nuvla.server.resources.mec.mm3-callback :as mm3-callback]
    [com.sixsq.nuvla.server.util.response :as r]
    [compojure.core :refer [ANY DELETE GET PATCH POST PUT defroutes let-routes]]))

(def ^:private mm1-pkg-base-uri (str p/service-context "mec/mm1/app_pkgm/v1"))
(def ^:private mm3-pkg-base-uri (str p/service-context "mec/mm3/app_pkgm/v1"))
(def ^:private mm1-lcm-base-uri (str p/service-context "mec/mm1/app_lcm/v1"))

(defn- with-full-id
  [request target-key]
  (let [{:keys [resourceName uuid]} (:params request)]
    (assoc-in request [:params target-key] (str resourceName "/" uuid))))

(defn- with-default-id
  [request target-key resource-name]
  (let [{:keys [uuid]} (:params request)]
    (assoc-in request [:params target-key] (str resource-name "/" uuid))))

(defn- with-base-path
  [request base-path]
  (assoc request :mec/base-path base-path))

(defn initialize
  []
  (app-lcm-subscription/initialize)
  (app-package-subscription/initialize))

(defroutes routes
  ;; Mm1 application package management - canonical routes
  (GET (str mm1-pkg-base-uri "/app_packages") request
    (app-package/query-app-packages (with-base-path request "/mec/mm1/app_pkgm/v1/app_packages")))
  (POST (str mm1-pkg-base-uri "/app_packages") request
    (app-package/create-app-package request))
  (let-routes [uri (str mm1-pkg-base-uri "/app_packages/:resourceName/:uuid")]
    (GET uri request
      (app-package/get-app-package (-> request
                                       (with-full-id :appPkgId)
                                       (with-base-path "/mec/mm1/app_pkgm/v1/app_packages"))))
    (PATCH uri request
      (app-package/update-app-package (with-full-id request :appPkgId)))
    (DELETE uri request
      (app-package/delete-app-package (with-full-id request :appPkgId))))
  (let-routes [uri (str mm1-pkg-base-uri "/app_packages/:uuid")]
    (GET uri request
      (app-package/get-app-package (-> request
                                       (with-default-id :appPkgId "module")
                                       (with-base-path "/mec/mm1/app_pkgm/v1/app_packages"))))
    (PATCH uri request
      (app-package/update-app-package (with-default-id request :appPkgId "module")))
    (DELETE uri request
      (app-package/delete-app-package (with-default-id request :appPkgId "module"))))
  (let-routes [uri (str mm1-pkg-base-uri "/app_packages/:resourceName/:uuid/appd")]
    (GET uri request
      (app-package/get-app-package-appd (with-full-id request :appPkgId))))
  (let-routes [uri (str mm1-pkg-base-uri "/app_packages/:uuid/appd")]
    (GET uri request
      (app-package/get-app-package-appd (with-default-id request :appPkgId "module"))))
  (let-routes [uri (str mm1-pkg-base-uri "/app_packages/:resourceName/:uuid/package_content")]
    (GET uri request
      (app-package/get-app-package-content (with-full-id request :appPkgId)))
    (PUT uri request
      (app-package/put-app-package-content (with-full-id request :appPkgId))))
  (let-routes [uri (str mm1-pkg-base-uri "/app_packages/:uuid/package_content")]
    (GET uri request
      (app-package/get-app-package-content (with-default-id request :appPkgId "module")))
    (PUT uri request
      (app-package/put-app-package-content (with-default-id request :appPkgId "module"))))
  (GET (str mm1-pkg-base-uri "/onboarded_app_packages") request
    (app-package/query-app-packages (with-base-path request "/mec/mm1/app_pkgm/v1/onboarded_app_packages")))
  (let-routes [uri (str mm1-pkg-base-uri "/onboarded_app_packages/:resourceName/:uuid")]
    (GET uri request
      (app-package/get-app-package (-> request
                                       (with-full-id :appPkgId)
                                       (with-base-path "/mec/mm1/app_pkgm/v1/onboarded_app_packages"))))
    (PATCH uri request
      (app-package/update-app-package (with-full-id request :appPkgId)))
    (DELETE uri request
      (app-package/delete-app-package (with-full-id request :appPkgId))))
  (let-routes [uri (str mm1-pkg-base-uri "/onboarded_app_packages/:uuid")]
    (GET uri request
      (app-package/get-app-package (-> request
                                       (with-default-id :appPkgId "module")
                                       (with-base-path "/mec/mm1/app_pkgm/v1/onboarded_app_packages"))))
    (PATCH uri request
      (app-package/update-app-package (with-default-id request :appPkgId "module")))
    (DELETE uri request
      (app-package/delete-app-package (with-default-id request :appPkgId "module"))))
  (let-routes [uri (str mm1-pkg-base-uri "/onboarded_app_packages/:resourceName/:uuid/appd")]
    (GET uri request
      (app-package/get-app-package-appd (with-full-id request :appPkgId))))
  (let-routes [uri (str mm1-pkg-base-uri "/onboarded_app_packages/:uuid/appd")]
    (GET uri request
      (app-package/get-app-package-appd (with-default-id request :appPkgId "module"))))
  (let-routes [uri (str mm1-pkg-base-uri "/onboarded_app_packages/:resourceName/:uuid/package_content")]
    (GET uri request
      (app-package/get-app-package-content (with-full-id request :appPkgId)))
    (PUT uri request
      (app-package/put-app-package-content (with-full-id request :appPkgId))))
  (let-routes [uri (str mm1-pkg-base-uri "/onboarded_app_packages/:uuid/package_content")]
    (GET uri request
      (app-package/get-app-package-content (with-default-id request :appPkgId "module")))
    (PUT uri request
      (app-package/put-app-package-content (with-default-id request :appPkgId "module"))))
  (GET (str mm1-pkg-base-uri "/subscriptions") request
    (app-package-subscription/list-subscriptions-handler (with-base-path request "/mec/mm1/app_pkgm/v1")))
  (POST (str mm1-pkg-base-uri "/subscriptions") request
    (app-package-subscription/create-subscription-handler (with-base-path request "/mec/mm1/app_pkgm/v1")))
  (let-routes [uri (str mm1-pkg-base-uri "/subscriptions/:resourceName/:uuid")]
    (GET uri request
      (app-package-subscription/get-subscription-handler (-> request
                                                             (with-full-id :id)
                                                             (with-base-path "/mec/mm1/app_pkgm/v1"))))
    (DELETE uri request
      (app-package-subscription/delete-subscription-handler (-> request
                                                                (with-full-id :id)
                                                                (with-base-path "/mec/mm1/app_pkgm/v1")))))
  (let-routes [uri (str mm1-pkg-base-uri "/subscriptions/:uuid")]
    (GET uri request
      (app-package-subscription/get-subscription-handler (-> request
                                                             (with-default-id :id "subscription")
                                                             (with-base-path "/mec/mm1/app_pkgm/v1"))))
    (DELETE uri request
      (app-package-subscription/delete-subscription-handler (-> request
                                                                (with-default-id :id "subscription")
                                                                (with-base-path "/mec/mm1/app_pkgm/v1")))))

  ;; Mm3 application package management subset with package subscriptions
  (GET (str mm3-pkg-base-uri "/app_packages") request
    (app-package/query-app-packages (with-base-path request "/mec/mm3/app_pkgm/v1/app_packages")))
  (let-routes [uri (str mm3-pkg-base-uri "/app_packages/:resourceName/:uuid")]
    (GET uri request
      (app-package/get-app-package (-> request
                                       (with-full-id :appPkgId)
                                       (with-base-path "/mec/mm3/app_pkgm/v1/app_packages"))))
    (PATCH uri request
      (app-package/update-app-package (with-full-id request :appPkgId))))
  (let-routes [uri (str mm3-pkg-base-uri "/app_packages/:uuid")]
    (GET uri request
      (app-package/get-app-package (-> request
                                       (with-default-id :appPkgId "module")
                                       (with-base-path "/mec/mm3/app_pkgm/v1/app_packages"))))
    (PATCH uri request
      (app-package/update-app-package (with-default-id request :appPkgId "module"))))
  (let-routes [uri (str mm3-pkg-base-uri "/app_packages/:resourceName/:uuid/appd")]
    (GET uri request
      (app-package/get-app-package-appd (with-full-id request :appPkgId))))
  (let-routes [uri (str mm3-pkg-base-uri "/app_packages/:uuid/appd")]
    (GET uri request
      (app-package/get-app-package-appd (with-default-id request :appPkgId "module"))))
  (let-routes [uri (str mm3-pkg-base-uri "/app_packages/:resourceName/:uuid/package_content")]
    (GET uri request
      (app-package/get-app-package-content (with-full-id request :appPkgId))))
  (let-routes [uri (str mm3-pkg-base-uri "/app_packages/:uuid/package_content")]
    (GET uri request
      (app-package/get-app-package-content (with-default-id request :appPkgId "module"))))
  (GET (str mm3-pkg-base-uri "/onboarded_app_packages") request
    (app-package/query-app-packages (with-base-path request "/mec/mm3/app_pkgm/v1/onboarded_app_packages")))
  (let-routes [uri (str mm3-pkg-base-uri "/onboarded_app_packages/:resourceName/:uuid")]
    (GET uri request
      (app-package/get-app-package (-> request
                                       (with-full-id :appPkgId)
                                       (with-base-path "/mec/mm3/app_pkgm/v1/onboarded_app_packages"))))
    (PATCH uri request
      (app-package/update-app-package (with-full-id request :appPkgId))))
  (let-routes [uri (str mm3-pkg-base-uri "/onboarded_app_packages/:uuid")]
    (GET uri request
      (app-package/get-app-package (-> request
                                       (with-default-id :appPkgId "module")
                                       (with-base-path "/mec/mm3/app_pkgm/v1/onboarded_app_packages"))))
    (PATCH uri request
      (app-package/update-app-package (with-default-id request :appPkgId "module"))))
  (let-routes [uri (str mm3-pkg-base-uri "/onboarded_app_packages/:resourceName/:uuid/appd")]
    (GET uri request
      (app-package/get-app-package-appd (with-full-id request :appPkgId))))
  (let-routes [uri (str mm3-pkg-base-uri "/onboarded_app_packages/:uuid/appd")]
    (GET uri request
      (app-package/get-app-package-appd (with-default-id request :appPkgId "module"))))
  (let-routes [uri (str mm3-pkg-base-uri "/onboarded_app_packages/:resourceName/:uuid/package_content")]
    (GET uri request
      (app-package/get-app-package-content (with-full-id request :appPkgId))))
  (let-routes [uri (str mm3-pkg-base-uri "/onboarded_app_packages/:uuid/package_content")]
    (GET uri request
      (app-package/get-app-package-content (with-default-id request :appPkgId "module"))))
  (GET (str mm3-pkg-base-uri "/subscriptions") request
    (app-package-subscription/list-subscriptions-handler (with-base-path request "/mec/mm3/app_pkgm/v1")))
  (POST (str mm3-pkg-base-uri "/subscriptions") request
    (app-package-subscription/create-subscription-handler (with-base-path request "/mec/mm3/app_pkgm/v1")))
  (let-routes [uri (str mm3-pkg-base-uri "/subscriptions/:resourceName/:uuid")]
    (GET uri request
      (app-package-subscription/get-subscription-handler (-> request
                                                             (with-full-id :id)
                                                             (with-base-path "/mec/mm3/app_pkgm/v1"))))
    (DELETE uri request
      (app-package-subscription/delete-subscription-handler (-> request
                                                                (with-full-id :id)
                                                                (with-base-path "/mec/mm3/app_pkgm/v1")))))
  (let-routes [uri (str mm3-pkg-base-uri "/subscriptions/:uuid")]
    (GET uri request
      (app-package-subscription/get-subscription-handler (-> request
                                                             (with-default-id :id "subscription")
                                                             (with-base-path "/mec/mm3/app_pkgm/v1"))))
    (DELETE uri request
      (app-package-subscription/delete-subscription-handler (-> request
                                                                (with-default-id :id "subscription")
                                                                (with-base-path "/mec/mm3/app_pkgm/v1")))))

  ;; Mm1 application lifecycle management - canonical routes
  (GET (str mm1-lcm-base-uri "/app_instances") request
    (app-lcm/list-app-instances-handler request))
  (POST (str mm1-lcm-base-uri "/app_instances") request
    (app-lcm/create-app-instance-handler request))
  (let-routes [uri (str mm1-lcm-base-uri "/app_instances/:resourceName/:uuid")]
    (GET uri request
      (app-lcm/get-app-instance-handler (with-full-id request :id)))
    (DELETE uri request
      (app-lcm/delete-app-instance-handler (with-full-id request :id))))
  (let-routes [uri (str mm1-lcm-base-uri "/app_instances/:resourceName/:uuid/instantiate")]
    (POST uri request
      (app-lcm/instantiate-app-instance-handler (with-full-id request :id))))
  (let-routes [uri (str mm1-lcm-base-uri "/app_instances/:resourceName/:uuid/terminate")]
    (POST uri request
      (app-lcm/terminate-app-instance-handler (with-full-id request :id))))
  (let-routes [uri (str mm1-lcm-base-uri "/app_instances/:resourceName/:uuid/operate")]
    (POST uri request
      (app-lcm/operate-app-instance-handler (with-full-id request :id))))
  (GET (str mm1-lcm-base-uri "/app_lcm_op_occs") request
    (app-lcm/list-app-lcm-op-occs-handler request))
  (let-routes [uri (str mm1-lcm-base-uri "/app_lcm_op_occs/:resourceName/:uuid")]
    (GET uri request
      (app-lcm/get-app-lcm-op-occ-handler (with-full-id request :id))))
  (let-routes [uri (str mm1-lcm-base-uri "/app_lcm_op_occs/:resourceName/:uuid/cancel")]
    (POST uri request
      (app-lcm/cancel-app-lcm-op-occ-handler (with-full-id request :id))))
  (let-routes [uri (str mm1-lcm-base-uri "/app_lcm_op_occs/:resourceName/:uuid/fail")]
    (POST uri request
      (app-lcm/fail-app-lcm-op-occ-handler (with-full-id request :id))))
  (let-routes [uri (str mm1-lcm-base-uri "/app_lcm_op_occs/:resourceName/:uuid/retry")]
    (POST uri request
      (app-lcm/retry-app-lcm-op-occ-handler (with-full-id request :id))))
  (GET (str mm1-lcm-base-uri "/subscriptions") request
    (app-lcm/list-subscriptions-handler request))
  (POST (str mm1-lcm-base-uri "/subscriptions") request
    (app-lcm/create-subscription-handler request))
  (let-routes [uri (str mm1-lcm-base-uri "/subscriptions/:resourceName/:uuid")]
    (GET uri request
      (app-lcm/get-subscription-handler (with-full-id request :id)))
    (DELETE uri request
      (app-lcm/delete-subscription-handler (with-full-id request :id))))

  ;; Internal southbound Mm3.003 callback receiver
  (POST (str p/service-context "mec/internal/mm3/app_lcm/v1/notifications") request
    (mm3-callback/handle-notification request))

  (ANY (str mm1-pkg-base-uri "*") request
    (throw (r/ex-bad-method request)))
  (ANY (str mm3-pkg-base-uri "*") request
    (throw (r/ex-bad-method request)))
  (ANY (str mm1-lcm-base-uri "*") request
    (throw (r/ex-bad-method request))))
