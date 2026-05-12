(ns com.sixsq.nuvla.server.resources.mec.error-handler
  "RFC 7807 ProblemDetails Error Handler for MEC 010-2 APIs
   
   Provides standardized error responses compliant with RFC 7807.
   All MEC API endpoints should use these error handling functions
   to ensure consistent error reporting.
   
   Standard: RFC 7807 (Problem Details for HTTP APIs)
   MEC baseline: ETSI GS MEC 010-2 v4.1.1"
  (:require
    [clojure.tools.logging :as log]))


;;
;; Error Type URIs (MEC 010-2 specific)
;;

(def ^:const error-types
  "MEC-specific error type URIs following RFC 7807"
  {:not-found            "https://docs.nuvla.io/mec/errors/not-found"
   :validation-error     "https://docs.nuvla.io/mec/errors/validation"
   :conflict             "https://docs.nuvla.io/mec/errors/conflict"
   :unauthorized         "https://docs.nuvla.io/mec/errors/unauthorized"
   :forbidden            "https://docs.nuvla.io/mec/errors/forbidden"
   :operation-not-allowed "https://docs.nuvla.io/mec/errors/operation-not-allowed"
   :invalid-state        "https://docs.nuvla.io/mec/errors/invalid-state"
   :resource-exhausted   "https://docs.nuvla.io/mec/errors/resource-exhausted"
   :mepm-error           "https://docs.nuvla.io/mec/errors/mepm-error"
   :internal-error       "https://docs.nuvla.io/mec/errors/internal"
   :timeout              "https://docs.nuvla.io/mec/errors/timeout"
   :bad-gateway          "https://docs.nuvla.io/mec/errors/bad-gateway"
   :service-unavailable  "https://docs.nuvla.io/mec/errors/service-unavailable"})


;;
;; Core ProblemDetails Constructor
;;

(defn problem-details
  "Creates an RFC 7807 ProblemDetails error response
   
   Args:
   - type: URI reference identifying the problem type (keyword or string)
   - title: Short, human-readable summary
   - status: HTTP status code
   - detail: Human-readable explanation specific to this occurrence
   - instance: URI reference identifying the specific occurrence
   - extensions: Map of additional problem-specific fields
   
   Returns: Map conforming to RFC 7807 ProblemDetails schema"
  ([type title status]
   (problem-details type title status nil nil nil))
  ([type title status detail]
   (problem-details type title status detail nil nil))
  ([type title status detail instance]
   (problem-details type title status detail instance nil))
  ([type title status detail instance extensions]
   (let [type-uri (if (keyword? type)
                    (get error-types type "about:blank")
                    type)]
     (cond-> {:type   type-uri
              :title  title
              :status status}
       detail (assoc :detail detail)
       instance (assoc :instance instance)
       extensions (merge extensions)))))


;;
;; 4xx Client Error Responses
;;

(defn bad-request
  "400 Bad Request - Malformed request syntax"
  ([detail]
   (bad-request detail nil nil))
  ([detail instance]
   (bad-request detail instance nil))
  ([detail instance extensions]
   (problem-details
     :validation-error
     "Bad Request"
     400
     detail
     instance
     extensions)))


(defn unauthorized
  "401 Unauthorized - Authentication required"
  ([detail]
   (unauthorized detail nil nil))
  ([detail instance]
   (unauthorized detail instance nil))
  ([detail instance extensions]
   (problem-details
     :unauthorized
     "Unauthorized"
     401
     detail
     instance
     extensions)))


(defn forbidden
  "403 Forbidden - Insufficient permissions"
  ([detail]
   (forbidden detail nil nil))
  ([detail instance]
   (forbidden detail instance nil))
  ([detail instance extensions]
   (problem-details
     :forbidden
     "Forbidden"
     403
     detail
     instance
     extensions)))


(defn not-found
  "404 Not Found - Resource does not exist"
  ([resource-type resource-id]
   (not-found resource-type resource-id nil))
  ([resource-type resource-id extensions]
   (problem-details
     :not-found
     "Resource Not Found"
     404
     (str resource-type " " resource-id " not found")
     resource-id
     extensions)))


(defn conflict
  "409 Conflict - Request conflicts with current state"
  ([detail]
   (conflict detail nil nil))
  ([detail instance]
   (conflict detail instance nil))
  ([detail instance extensions]
   (problem-details
     :conflict
     "Resource Conflict"
     409
     detail
     instance
     extensions)))


(defn invalid-state
  "409 Conflict - Operation not allowed in current state"
  [resource-id current-state expected-state operation]
  (problem-details
    :invalid-state
    "Invalid State for Operation"
    409
    (str "Cannot perform " operation " on resource in state " current-state
         ". Expected state: " expected-state)
    resource-id
    {:current-state  current-state
     :expected-state expected-state
     :operation      operation}))


(defn operation-not-allowed
  "422 Unprocessable Entity - Operation not allowed"
  ([detail]
   (operation-not-allowed detail nil nil))
  ([detail instance]
   (operation-not-allowed detail instance nil))
  ([detail instance extensions]
   (problem-details
     :operation-not-allowed
     "Operation Not Allowed"
     422
     detail
     instance
     extensions)))


(defn resource-exhausted
  "429 Too Many Requests or 507 Insufficient Storage - Resource exhausted"
  [resource-type detail]
  (problem-details
    :resource-exhausted
    "Resource Exhausted"
    507
    detail
    nil
    {:resource-type resource-type}))


;;
;; 5xx Server Error Responses
;;

(defn internal-error
  "500 Internal Server Error - Unexpected server error"
  ([detail]
   (internal-error detail nil nil))
  ([detail instance]
   (internal-error detail instance nil))
  ([detail instance extensions]
   (problem-details
     :internal-error
     "Internal Server Error"
     500
     detail
     instance
     extensions)))


(defn mepm-error
  "502 Bad Gateway - MEPM communication error"
  [mepm-endpoint detail]
  (problem-details
    :mepm-error
    "MEPM Communication Error"
    502
    detail
    nil
    {:mepm-endpoint mepm-endpoint}))


(defn service-unavailable
  "503 Service Unavailable - Service temporarily unavailable"
  ([detail]
   (service-unavailable detail nil nil))
  ([detail instance]
   (service-unavailable detail instance nil))
  ([detail instance extensions]
   (problem-details
     :service-unavailable
     "Service Unavailable"
     503
     detail
     instance
     extensions)))


(defn gateway-timeout
  "504 Gateway Timeout - MEPM timeout"
  [mepm-endpoint operation]
  (problem-details
    :timeout
    "Gateway Timeout"
    504
    (str "Timeout waiting for MEPM response during " operation)
    nil
    {:mepm-endpoint mepm-endpoint
     :operation      operation}))


;;
;; Validation Error Helpers
;;

(defn validation-error
  "400 Bad Request - Schema validation error
   
   Args:
   - field: Field name that failed validation
   - reason: Why validation failed
   - value: The invalid value (optional)"
  ([field reason]
   (validation-error field reason nil))
  ([field reason value]
   (bad-request
     (str "Validation failed for field '" field "': " reason)
     nil
     {:field  field
      :reason reason
      :value  value})))


(defn missing-required-field
  "400 Bad Request - Required field missing"
  [field]
  (validation-error
    field
    "Required field is missing"
    nil))


(defn invalid-field-value
  "400 Bad Request - Invalid field value"
  [field value expected]
  (validation-error
    field
    (str "Invalid value. Expected: " expected)
    value))


(defn invalid-enum-value
  "400 Bad Request - Invalid enum value"
  [field value valid-values]
  (validation-error
    field
    (str "Invalid enum value. Valid values: " (clojure.string/join ", " valid-values))
    value))


;;
;; Exception Handling Helpers
;;

(defn exception->problem-details
  "Converts an exception to RFC 7807 ProblemDetails
   
   Handles:
   - ExceptionInfo with :status in ex-data
   - Known exception types
   - Generic exceptions"
  [exception & {:keys [instance operation]}]
  (if-let [ex-data (when (instance? clojure.lang.ExceptionInfo exception)
                     (ex-data exception))]
    ;; Handle ExceptionInfo with status
    (let [status  (:status ex-data 500)
          message (ex-message exception)
          type-keyword (cond
                         (= status 404) :not-found
                         (= status 409) :conflict
                         (= status 422) :operation-not-allowed
                         (>= status 500) :internal-error
                         :else :validation-error)
          extensions (cond-> (dissoc ex-data :status)
                       operation (assoc :operation operation))]
      (problem-details
        type-keyword
        (case status
          404 "Resource Not Found"
          409 "Resource Conflict"
          422 "Operation Not Allowed"
          500 "Internal Server Error"
          502 "Bad Gateway"
          503 "Service Unavailable"
          "Bad Request")
        status
        message
        instance
        extensions))
    
    ;; Handle generic exceptions
    (do
      (log/error exception "Unexpected exception during" operation)
      (internal-error
        (or (ex-message exception) "An unexpected error occurred")
        instance
        {:exception-type (str (type exception))
         :operation      operation}))))


;;
;; Logging Helpers
;;

(defn log-and-return-error
  "Logs error details and returns ProblemDetails response
   
   Useful for error handling in catch blocks"
  [problem-details-map operation context]
  (let [status (:status problem-details-map)
        title  (:title problem-details-map)
        detail (:detail problem-details-map)]
    (if (>= status 500)
      (log/error "Server error during" operation "-" title ":" detail "| Context:" context)
      (log/warn "Client error during" operation "-" title ":" detail "| Context:" context))
    problem-details-map))


;;
;; Testing Helper
;;

(defn problem-details?
  "Predicate to check if a map is a valid RFC 7807 ProblemDetails response"
  [m]
  (and (map? m)
       (contains? m :type)
       (contains? m :title)
       (contains? m :status)
       (number? (:status m))
       (>= (:status m) 400)
       (< (:status m) 600)))
