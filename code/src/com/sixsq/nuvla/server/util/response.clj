(ns com.sixsq.nuvla.server.util.response
  "Utilities for creating ring responses and exceptions with embedded ring
   responses."
  (:require
    [com.sixsq.nuvla.server.util.general :as gen-util]
    [ring.util.response :as r]))


(defn response-created
  "Provides a created response (201) with the Location header given by the
   identifier and provides the Set-Cookie header with the given cookie, if
   the cookie value is not nil."
  [id & [[cookie-name cookie]]]
  (cond-> {:status  201
           :headers {"Location" id}
           :body    {:status      201
                     :message     (str id " created")
                     :resource-id id}}
          cookie (assoc :cookies {cookie-name cookie})))


(defn response-final-redirect
  "Provides a created response (303) with the Location header given by the
   identifier and provides the Set-Cookie header with the given cookie, if
   the cookie value is not nil."
  [location & [[cookie-name cookie]]]
  (cond-> {:status 303, :headers {"Location" location}}
          cookie (assoc :cookies {cookie-name cookie})))


(defn json-response
  "Provides a simple 200 response with the content type header set to json."
  [body]
  (-> body
      (r/response)
      (r/content-type "application/json")))


(defn text-response
  "Provides a simple 200 response with the content type header set to plain text."
  [body]
  (-> body
      (r/response)
      (r/content-type "text/plain")))

(defn csv-response
  "Provides a 200 response with the content type header set to csv."
  [filename body]
  (-> body
      (r/response)
      (r/content-type "text/csv")
      (r/header "Content-disposition" (str "attachment;filename=" filename))))

(defn map-response
  "Provides a generic map response with the given message, status, resource
   ID, and location. Only the message and status are required."
  ([msg status]
   (map-response msg status nil nil))
  ([msg status id]
   (map-response msg status id nil))
  ([msg status id location]
   (let [resp (-> (cond-> {:status status, :message msg}
                          id (assoc :resource-id id)
                          location (assoc :location location))
                  json-response
                  (r/status status))]
     (cond-> resp
             location (update-in [:headers "Location"] (constantly location))))))


(defn ex-response
  "Provides a generic exception response with the given message, status,
   resource identifier, and location information."
  ([msg status]
   (ex-info msg (map-response msg status)))
  ([msg status id]
   (ex-info msg (map-response msg status id)))
  ([msg status id location]
   (ex-info msg (map-response msg status id location))))


(defn response-deleted
  [id]
  (map-response (str id " deleted") 200 id))


(defn response-updated
  [id]
  (map-response (str "updated " id) 200 id))


(defn response-not-found
  [id]
  (map-response (str id " not found") 404 id))


(defn response-error
  [msg]
  (map-response (str "unexpected error occurred: " msg) 500))


(defn response-conflict
  [id]
  (map-response (str "conflict with " id) 409 id))


(defn ex-bad-request
  "Provides an ExceptionInfo exception when the input is not valid. This is a
   400 status response. If the message is not provided, a generic one is used."
  [& [msg]]
  (ex-response (or msg "invalid request") 400))


(defn ex-not-found
  "Provides an ExceptionInfo exception when a resource is not found. This is a
   404 status response and the provided id should be the resource identifier."
  [id]
  (ex-response (str id " not found") 404 id))


(defn ex-conflict
  "Provides an ExceptionInfo exception when there is a conflict. This is a 409
   status response and the provided id should be the resource identifier."
  [id]
  (ex-response (str "conflict with " id) 409 id))


(defn ex-unauthorized
  "Provides an ExceptionInfo exception when the user is not authorized to
   access the resource. This is a 403 status response and the provided id
   should be the resource identifier or the username."
  [id]
  (let [msg (if id
              (str "invalid credentials for '" id "'")
              "credentials required")]
    (ex-response msg 403 id)))


(defn ex-bad-method
  "Provides an ExceptionInfo exception when an unsupported method is used on a
   resource. This is a 405 status code. Information from the request is used to
   provide a reasonable message."
  [{:keys [uri request-method] :as _request}]
  (let [msg (format "invalid method (%s) for %s"
                    (name (or request-method "UNKNOWN"))
                    (or uri "UNKNOWN"))]
    (ex-response msg 405 uri)))


(defn ex-bad-action
  "Provides an ExceptionInfo exception when an unsupported resource action is
   used. This is a 404 status code. Information from the request and the action
   are used to provide a reasonable message."
  [{:keys [uri request-method] :as _request} action]
  (let [msg (format "undefined action (%s, %s) for %s"
                    (name (or request-method "UNKNOWN"))
                    action
                    (or uri "UNKNOWN"))]
    (ex-response msg 404 uri)))


(defn ex-bad-CIMI-filter
  [parse-failure]
  (ex-response (str "Invalid CIMI filter. " (prn-str parse-failure)) 400))


(defn ex-redirect
  "Provides an exception that will redirect (303) to the given redirect-url, by
   setting the Location header. The message is added as an 'error' query
   parameter to the redirect-url."
  [msg id redirect-url]
  (let [query (str "?error=" (gen-util/encode-uri-component msg))]
    (ex-response msg 303 id (str redirect-url query))))

(defn rethrow-response
  [{{:keys [resource-id status message]} :body :as response}]
  (if (and (r/response? response)
           (> status 399))
    (throw (ex-response message status resource-id))
    (let [msg "rethrow-response bad argument"]
      (throw (ex-info msg (response-error msg))))))

(defn status-200?
  [{:keys [status] :as _response}]
  (= status 200))

(defn configurable-check-response
  [response-ok? on-success on-error]
  (fn [response]
    (if (response-ok? response)
      (on-success response)
      (on-error response))))

(def throw-response-not-200
  (configurable-check-response
    status-200? identity rethrow-response))

(defn response-body
  [response]
  (:body response))

(def ignore-response-not-200
  (configurable-check-response
    status-200? identity (constantly nil)))
