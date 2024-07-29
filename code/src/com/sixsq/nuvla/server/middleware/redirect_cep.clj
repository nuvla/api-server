(ns com.sixsq.nuvla.server.middleware.redirect-cep
  "Ring middleware that will redirect to the cloud entry point on any request
   that doesn't start with /api/."
  (:require
    [ring.util.response :as r]))


(defn redirect-cep [wrapped-handler]
  (fn [{:keys [uri] :as request}]
    (if (and (string? uri) (re-matches #"/api/.+" uri))
      (wrapped-handler request)
      (r/redirect "/api/cloud-entry-point"))))
