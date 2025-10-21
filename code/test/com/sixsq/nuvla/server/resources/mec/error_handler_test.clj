(ns com.sixsq.nuvla.server.resources.mec.error-handler-test
  "Tests for RFC 7807 Error Handler"
  (:require
    [clojure.test :refer [deftest is testing]]
    [com.sixsq.nuvla.server.resources.mec.error-handler :as eh]))


;;
;; Core ProblemDetails Tests
;;

(deftest test-problem-details-minimal
  (testing "Create minimal ProblemDetails with type, title, status"
    (let [pd (eh/problem-details :not-found "Not Found" 404)]
      (is (= "https://docs.nuvla.io/mec/errors/not-found" (:type pd)))
      (is (= "Not Found" (:title pd)))
      (is (= 404 (:status pd)))
      (is (nil? (:detail pd)))
      (is (nil? (:instance pd))))))


(deftest test-problem-details-with-detail
  (testing "Create ProblemDetails with detail"
    (let [pd (eh/problem-details :validation-error "Validation Error" 400 "Missing required field")]
      (is (= "https://docs.nuvla.io/mec/errors/validation" (:type pd)))
      (is (= "Validation Error" (:title pd)))
      (is (= 400 (:status pd)))
      (is (= "Missing required field" (:detail pd))))))


(deftest test-problem-details-with-instance
  (testing "Create ProblemDetails with instance URI"
    (let [pd (eh/problem-details :not-found "Not Found" 404 "Resource not found" "app-instance-123")]
      (is (= "app-instance-123" (:instance pd))))))


(deftest test-problem-details-with-extensions
  (testing "Create ProblemDetails with extension fields"
    (let [pd (eh/problem-details :conflict "Conflict" 409 "State conflict" "app-123" {:current-state "STARTED"})]
      (is (= "STARTED" (:current-state pd))))))


(deftest test-problem-details-custom-type-uri
  (testing "Create ProblemDetails with custom type URI"
    (let [pd (eh/problem-details "https://example.com/custom-error" "Custom" 418)]
      (is (= "https://example.com/custom-error" (:type pd))))))


;;
;; 4xx Client Error Tests
;;

(deftest test-bad-request
  (testing "Create 400 Bad Request error"
    (let [pd (eh/bad-request "Invalid JSON")]
      (is (= 400 (:status pd)))
      (is (= "Bad Request" (:title pd)))
      (is (= "Invalid JSON" (:detail pd))))))


(deftest test-unauthorized
  (testing "Create 401 Unauthorized error"
    (let [pd (eh/unauthorized "Authentication required")]
      (is (= 401 (:status pd)))
      (is (= "Unauthorized" (:title pd))))))


(deftest test-forbidden
  (testing "Create 403 Forbidden error"
    (let [pd (eh/forbidden "Insufficient permissions" "user-123")]
      (is (= 403 (:status pd)))
      (is (= "Forbidden" (:title pd)))
      (is (= "user-123" (:instance pd))))))


(deftest test-not-found
  (testing "Create 404 Not Found error"
    (let [pd (eh/not-found "AppInstance" "app-123")]
      (is (= 404 (:status pd)))
      (is (= "Resource Not Found" (:title pd)))
      (is (= "AppInstance app-123 not found" (:detail pd)))
      (is (= "app-123" (:instance pd))))))


(deftest test-conflict
  (testing "Create 409 Conflict error"
    (let [pd (eh/conflict "Resource already exists" "app-123")]
      (is (= 409 (:status pd)))
      (is (= "Resource Conflict" (:title pd))))))


(deftest test-invalid-state
  (testing "Create 409 Invalid State error with state details"
    (let [pd (eh/invalid-state "app-123" "STARTED" "STOPPED" "terminate")]
      (is (= 409 (:status pd)))
      (is (= "Invalid State for Operation" (:title pd)))
      (is (= "app-123" (:instance pd)))
      (is (= "STARTED" (:current-state pd)))
      (is (= "STOPPED" (:expected-state pd)))
      (is (= "terminate" (:operation pd))))))


(deftest test-operation-not-allowed
  (testing "Create 422 Operation Not Allowed error"
    (let [pd (eh/operation-not-allowed "Cannot delete active instance")]
      (is (= 422 (:status pd)))
      (is (= "Operation Not Allowed" (:title pd))))))


(deftest test-resource-exhausted
  (testing "Create 507 Resource Exhausted error"
    (let [pd (eh/resource-exhausted "CPU" "No available CPU resources")]
      (is (= 507 (:status pd)))
      (is (= "Resource Exhausted" (:title pd)))
      (is (= "CPU" (:resource-type pd))))))


;;
;; 5xx Server Error Tests
;;

(deftest test-internal-error
  (testing "Create 500 Internal Server Error"
    (let [pd (eh/internal-error "Database connection failed")]
      (is (= 500 (:status pd)))
      (is (= "Internal Server Error" (:title pd)))
      (is (= "Database connection failed" (:detail pd))))))


(deftest test-mepm-error
  (testing "Create 502 MEPM Error"
    (let [pd (eh/mepm-error "http://mepm:8080" "MEPM returned invalid response")]
      (is (= 502 (:status pd)))
      (is (= "MEPM Communication Error" (:title pd)))
      (is (= "http://mepm:8080" (:mepm-endpoint pd))))))


(deftest test-service-unavailable
  (testing "Create 503 Service Unavailable error"
    (let [pd (eh/service-unavailable "Service temporarily down")]
      (is (= 503 (:status pd)))
      (is (= "Service Unavailable" (:title pd))))))


(deftest test-gateway-timeout
  (testing "Create 504 Gateway Timeout error"
    (let [pd (eh/gateway-timeout "http://mepm:8080" "instantiate")]
      (is (= 504 (:status pd)))
      (is (= "Gateway Timeout" (:title pd)))
      (is (= "http://mepm:8080" (:mepm-endpoint pd)))
      (is (= "instantiate" (:operation pd))))))


;;
;; Validation Error Helper Tests
;;

(deftest test-validation-error
  (testing "Create validation error for field"
    (let [pd (eh/validation-error "appName" "Must not be empty")]
      (is (= 400 (:status pd)))
      (is (= "appName" (:field pd)))
      (is (= "Must not be empty" (:reason pd))))))


(deftest test-validation-error-with-value
  (testing "Create validation error with invalid value"
    (let [pd (eh/validation-error "cpu" "Must be positive" -1)]
      (is (= "cpu" (:field pd)))
      (is (= -1 (:value pd))))))


(deftest test-missing-required-field
  (testing "Create missing required field error"
    (let [pd (eh/missing-required-field "appDId")]
      (is (= 400 (:status pd)))
      (is (= "appDId" (:field pd)))
      (is (clojure.string/includes? (:detail pd) "Required field is missing")))))


(deftest test-invalid-field-value
  (testing "Create invalid field value error"
    (let [pd (eh/invalid-field-value "operationalState" "UNKNOWN" "STARTED or STOPPED")]
      (is (= "operationalState" (:field pd)))
      (is (= "UNKNOWN" (:value pd)))
      (is (clojure.string/includes? (:detail pd) "Expected: STARTED or STOPPED")))))


(deftest test-invalid-enum-value
  (testing "Create invalid enum value error"
    (let [pd (eh/invalid-enum-value "operation" "DELETE" ["INSTANTIATE" "TERMINATE" "OPERATE"])]
      (is (= "operation" (:field pd)))
      (is (= "DELETE" (:value pd)))
      (is (clojure.string/includes? (:detail pd) "INSTANTIATE")))))


;;
;; Exception Handling Tests
;;

(deftest test-exception-to-problem-details-with-status
  (testing "Convert ExceptionInfo with :status to ProblemDetails"
    (let [ex (ex-info "Resource not found" {:status 404 :resource-id "app-123"})
          pd (eh/exception->problem-details ex :instance "app-123")]
      (is (= 404 (:status pd)))
      (is (= "Resource Not Found" (:title pd)))
      (is (= "Resource not found" (:detail pd)))
      (is (= "app-123" (:instance pd)))
      (is (= "app-123" (:resource-id pd))))))


(deftest test-exception-to-problem-details-conflict
  (testing "Convert 409 ExceptionInfo to ProblemDetails"
    (let [ex (ex-info "State conflict" {:status 409 :current-state "STARTED"})
          pd (eh/exception->problem-details ex)]
      (is (= 409 (:status pd)))
      (is (= "Resource Conflict" (:title pd)))
      (is (= "STARTED" (:current-state pd))))))


(deftest test-exception-to-problem-details-generic
  (testing "Convert generic exception to 500 ProblemDetails"
    (let [ex (Exception. "Unexpected error")
          pd (eh/exception->problem-details ex :operation "instantiate")]
      (is (= 500 (:status pd)))
      (is (= "Internal Server Error" (:title pd)))
      (is (= "Unexpected error" (:detail pd)))
      (is (= "instantiate" (:operation pd))))))


(deftest test-exception-to-problem-details-with-operation
  (testing "Convert exception with operation context"
    (let [ex (ex-info "Operation failed" {:status 502})
          pd (eh/exception->problem-details ex :operation "terminate" :instance "app-123")]
      (is (= 502 (:status pd)))
      (is (= "app-123" (:instance pd)))
      (is (= "terminate" (:operation pd))))))


;;
;; Helper Function Tests
;;

(deftest test-problem-details-predicate-valid
  (testing "Recognize valid ProblemDetails map"
    (let [pd (eh/not-found "AppInstance" "app-123")]
      (is (eh/problem-details? pd)))))


(deftest test-problem-details-predicate-invalid-missing-fields
  (testing "Reject map missing required fields"
    (is (not (eh/problem-details? {:type "test" :title "Test"})))
    (is (not (eh/problem-details? {:status 404 :title "Test"})))))


(deftest test-problem-details-predicate-invalid-status
  (testing "Reject map with invalid status code"
    (is (not (eh/problem-details? {:type "test" :title "Test" :status 200}))) ; 2xx not error
    (is (not (eh/problem-details? {:type "test" :title "Test" :status 600}))))) ; > 599


(deftest test-problem-details-predicate-not-map
  (testing "Reject non-map values"
    (is (not (eh/problem-details? "not a map")))
    (is (not (eh/problem-details? nil)))
    (is (not (eh/problem-details? 404)))))


;;
;; Error Type URI Tests
;;

(deftest test-error-type-uris-complete
  (testing "Verify all error types have URIs"
    (is (string? (eh/error-types :not-found)))
    (is (string? (eh/error-types :validation-error)))
    (is (string? (eh/error-types :conflict)))
    (is (string? (eh/error-types :unauthorized)))
    (is (string? (eh/error-types :forbidden)))
    (is (string? (eh/error-types :operation-not-allowed)))
    (is (string? (eh/error-types :invalid-state)))
    (is (string? (eh/error-types :resource-exhausted)))
    (is (string? (eh/error-types :mepm-error)))
    (is (string? (eh/error-types :internal-error)))
    (is (string? (eh/error-types :timeout)))
    (is (string? (eh/error-types :bad-gateway)))
    (is (string? (eh/error-types :service-unavailable)))))


(deftest test-error-type-uris-format
  (testing "Verify error type URIs are well-formed"
    (doseq [[_ uri] eh/error-types]
      (is (clojure.string/starts-with? uri "https://"))
      (is (clojure.string/includes? uri "nuvla.io/mec/errors/")))))


;;
;; Module Completeness Test
;;

(deftest test-error-handler-complete
  (testing "Verify all expected functions are available"
    (let [expected-fns ['problem-details
                        'bad-request
                        'unauthorized
                        'forbidden
                        'not-found
                        'conflict
                        'invalid-state
                        'operation-not-allowed
                        'resource-exhausted
                        'internal-error
                        'mepm-error
                        'service-unavailable
                        'gateway-timeout
                        'validation-error
                        'missing-required-field
                        'invalid-field-value
                        'invalid-enum-value
                        'exception->problem-details
                        'log-and-return-error
                        'problem-details?]]
      (doseq [fn-name expected-fns]
        (is (some? (ns-resolve 'com.sixsq.nuvla.server.resources.mec.error-handler fn-name))
            (str "Function " fn-name " should be defined"))))))
