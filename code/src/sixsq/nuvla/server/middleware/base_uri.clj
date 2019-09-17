(ns sixsq.nuvla.server.middleware.base-uri
  "middleware to add the :base-uri key and value to the request"
  (:require
    [sixsq.nuvla.server.app.params :as p]))

(defn get-host-port
  "Gets the originating host and port, preferring the 'forwarded'
   headers, if they exist. If neither the x-forwarded-host or host
   headers exist, then the default is the server-name and server-port
   from the ring request."
  [{:keys [headers server-name server-port]}]
  (or (get headers "x-forwarded-host")
      (get headers "host")
      (format "%s:%s" server-name server-port)))

(defn get-scheme
  "Get the scheme for the originating host, preferring the 'forwarded'
   header, it it exists."
  [{:keys [headers scheme]}]
  (or (get headers "x-forwarded-proto")
      (name scheme)))

(defn construct-base-uri
  ([req]
   (construct-base-uri req p/service-context))
  ([req service-context]
   (format "%s://%s%s" (get-scheme req) (get-host-port req) service-context)))

(defn wrap-base-uri
  "adds the :base-uri key to the request with the base URI value"
  [handler]
  (fn [req]
    (let [base-uri (construct-base-uri req)]
      (-> req
          (assoc :base-uri base-uri)
          (handler)))))
