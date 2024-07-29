(ns com.sixsq.nuvla.server.resources.hook
  "
The `hook` resource is a non standard cimi resource that provides an access
for events driven workflows.
"
  (:require
    [compojure.core :refer [ANY defroutes let-routes]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.hook-oidc-session :as oidc-session]
    [com.sixsq.nuvla.server.resources.hook-oidc-user :as oidc-user]
    [com.sixsq.nuvla.server.resources.hook-reset-password :as reset-password]
    [com.sixsq.nuvla.server.resources.hook-stripe-oauth :as stripe-oauth]))

;;
;; utilities
;;

(def ^:const resource-type (u/ns->type *ns*))


(def resource-acl {:owners   ["group/nuvla-admin"]
                   :view-acl ["group/nuvla-anon"]})


(defroutes routes
           (ANY (str p/service-context resource-type "/" stripe-oauth/action) request
             (stripe-oauth/execute request))
           (ANY (str p/service-context resource-type "/" reset-password/action) request
             (reset-password/execute request))
           (let-routes [uri (str p/service-context resource-type "/" oidc-user/action "/:instance")]
             (ANY uri request
               (oidc-user/execute request)))
           (let-routes [uri (str p/service-context resource-type "/" oidc-session/action "/:instance")]
             (ANY uri request
               (oidc-session/execute request)))
           (ANY (str p/service-context resource-type "/" oidc-user/action) request
             (oidc-user/execute request))
           (ANY (str p/service-context resource-type "/" oidc-session/action) request
             (oidc-session/execute request)))

