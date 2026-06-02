# Mm3 Interface API Reference

## Overview

The Mm3 interface provides communication between the MEC Orchestrator (MEO) and MEC Platform Manager (MEPM) according to ETSI GS MEC 003 specification. This document describes the client API for interacting with MEPM endpoints.

## Table of Contents

- [Client Functions](#client-functions)
  - [Health Monitoring](#health-monitoring)
  - [Capability Management](#capability-management)
  - [Resource Management](#resource-management)
  - [Platform Information](#platform-information)
  - [Configuration](#configuration)
  - [Application Lifecycle](#application-lifecycle)
- [Request/Response Formats](#requestresponse-formats)
- [Error Handling](#error-handling)
- [Usage Examples](#usage-examples)

## Client Functions

All functions are in the `com.sixsq.nuvla.server.resources.mec.mm3-client` namespace.

### Health Monitoring

#### `check-health`

Queries the health status of a MEPM platform.

**Signature:**
```clojure
(check-health endpoint & {:keys [connect-timeout read-timeout retry-attempts retry-delay]
                          :or {connect-timeout 5000
                               read-timeout 10000
                               retry-attempts 3
                               retry-delay 1000}})
```

**Parameters:**
- `endpoint` (string, required): MEPM base URL (e.g., "http://mepm.example.com:8080")
- `connect-timeout` (int, optional): Connection timeout in ms (default: 5000)
- `read-timeout` (int, optional): Read timeout in ms (default: 10000)
- `retry-attempts` (int, optional): Number of retry attempts (default: 3)
- `retry-delay` (int, optional): Delay between retries in ms (default: 1000)

**Returns:**
```clojure
{:success? true
 :status 200
 :data {:status "online"
        :timestamp "2025-10-21T10:30:00Z"
        :details "All systems operational"}}
```

**Error Response:**
```clojure
{:success? false
 :status 503
 :error :service-unavailable
 :message "MEPM is degraded"}
```

#### `healthy?`

Convenience function to check if MEPM is healthy.

**Signature:**
```clojure
(healthy? endpoint)
```

**Returns:** Boolean (true if MEPM status is "online")

---

### Capability Management

#### `query-capabilities`

Retrieves the capabilities supported by the MEPM platform.

**Signature:**
```clojure
(query-capabilities endpoint & {:keys [connect-timeout read-timeout retry-attempts retry-delay]
                                :or {connect-timeout 5000
                                     read-timeout 10000
                                     retry-attempts 3
                                     retry-delay 1000}})
```

**Parameters:**
- `endpoint` (string, required): MEPM base URL
- Options: Same as `check-health`

**Returns:**
```clojure
{:success? true
 :status 200
 :data {:capabilities ["mec-app-support"
                       "radio-network-information"
                       "location-services"
                       "bandwidth-management"]
        :api-version "2.1.1"
        :supported-features ["auto-scaling" "multi-tenancy"]}}
```

---

### Resource Management

#### `query-resources`

Retrieves available resources on the MEPM platform.

**Signature:**
```clojure
(query-resources endpoint & {:keys [connect-timeout read-timeout retry-attempts retry-delay]
                             :or {connect-timeout 5000
                                  read-timeout 10000
                                  retry-attempts 3
                                  retry-delay 1000}})
```

**Parameters:**
- `endpoint` (string, required): MEPM base URL
- Options: Same as `check-health`

**Returns:**
```clojure
{:success? true
 :status 200
 :data {:compute {:cpu-cores 128
                  :memory-gb 512
                  :storage-gb 10000}
        :network {:bandwidth-gbps 100
                  :latency-ms 1}
        :accelerators [{:type "GPU"
                        :model "NVIDIA A100"
                        :count 8}]
        :availability-zones ["zone-1" "zone-2"]}}
```

---

### Platform Information

#### `query-platform-info`

Retrieves general information about the MEPM platform.

**Signature:**
```clojure
(query-platform-info endpoint & {:keys [connect-timeout read-timeout retry-attempts retry-delay]
                                 :or {connect-timeout 5000
                                      read-timeout 10000
                                      retry-attempts 3
                                      retry-delay 1000}})
```

**Parameters:**
- `endpoint` (string, required): MEPM base URL
- Options: Same as `check-health`

**Returns:**
```clojure
{:success? true
 :status 200
 :data {:platform-id "mepm-prod-01"
        :name "Production MEPM"
        :version "3.2.1"
        :vendor "Nuvla"
        :location {:city "Geneva"
                   :country "Switzerland"
                   :coordinates {:lat 46.2044
                                :lon 6.1432}}
        :contact {:email "support@example.com"
                  :phone "+41-22-xxx-xxxx"}}}
```

---

### Configuration

#### `configure-platform`

Applies configuration updates to the MEPM platform.

**Signature:**
```clojure
(configure-platform endpoint config & {:keys [connect-timeout read-timeout retry-attempts retry-delay]
                                       :or {connect-timeout 5000
                                            read-timeout 10000
                                            retry-attempts 3
                                            retry-delay 1000}})
```

**Parameters:**
- `endpoint` (string, required): MEPM base URL
- `config` (map, required): Configuration parameters
- Options: Same as `check-health`

**Configuration Schema:**
```clojure
{:dns-config {:servers ["8.8.8.8" "8.8.4.4"]}
 :network {:default-gateway "192.168.1.1"
           :subnet "192.168.1.0/24"}
 :security {:enable-tls true
            :certificate-path "/etc/certs/mepm.crt"}
 :monitoring {:metrics-interval 60
              :log-level "info"}}
```

**Returns:**
```clojure
{:success? true
 :status 200
 :data {:message "Configuration applied successfully"
        :applied-at "2025-10-21T10:30:00Z"
        :restart-required false}}
```

---

### Application Lifecycle

#### `create-app-instance`

Creates a new MEC application instance on the MEPM.

**Signature:**
```clojure
(create-app-instance endpoint app-descriptor & {:keys [connect-timeout read-timeout retry-attempts retry-delay]
                                                :or {connect-timeout 5000
                                                     read-timeout 30000
                                                     retry-attempts 3
                                                     retry-delay 1000}})
```

**Parameters:**
- `endpoint` (string, required): MEPM base URL
- `app-descriptor` (map, required): Application deployment descriptor
- Options: Same as `check-health` (note: longer default read-timeout)

**Application Descriptor Schema:**
```clojure
{:app-name "video-analytics"
 :app-version "1.0.0"
 :app-package-url "https://registry.example.com/apps/video-analytics:1.0.0"
 :resources {:cpu-cores 4
             :memory-gb 8
             :storage-gb 50}
 :network {:interfaces [{:name "net0"
                         :type "bridge"
                         :ip "192.168.100.10"}]}
 :environment {:VIDEO_SOURCE "rtsp://camera.example.com/stream"
               :MODEL_PATH "/models/yolov8.onnx"}
 :dns-rules [{:domain "analytics.local"
              :ip-address "192.168.100.10"}]
 :traffic-rules [{:filter-type "FLOW"
                  :priority 1
                  :action "FORWARD"}]}
```

**Returns:**
```clojure
{:success? true
 :status 201
 :data {:instance-id "app-inst-12345"
        :status "INSTANTIATING"
        :created-at "2025-10-21T10:30:00Z"
        :endpoints {:management "https://192.168.100.10:8443"
                    :service "https://192.168.100.10:443"}}}
```

#### `get-app-instance`

Retrieves information about a specific application instance.

**Signature:**
```clojure
(get-app-instance endpoint instance-id & {:keys [connect-timeout read-timeout retry-attempts retry-delay]
                                          :or {connect-timeout 5000
                                               read-timeout 10000
                                               retry-attempts 3
                                               retry-delay 1000}})
```

**Parameters:**
- `endpoint` (string, required): MEPM base URL
- `instance-id` (string, required): Application instance ID
- Options: Same as `check-health`

**Returns:**
```clojure
{:success? true
 :status 200
 :data {:instance-id "app-inst-12345"
        :app-name "video-analytics"
        :status "RUNNING"
        :health "HEALTHY"
        :created-at "2025-10-21T10:30:00Z"
        :updated-at "2025-10-21T10:31:30Z"
        :resources {:cpu-usage 45
                    :memory-usage 62
                    :storage-usage 15}
        :metrics {:requests-per-sec 1250
                  :avg-latency-ms 12}}}
```

#### `list-app-instances`

Lists all application instances on the MEPM.

**Signature:**
```clojure
(list-app-instances endpoint & {:keys [connect-timeout read-timeout retry-attempts retry-delay
                                       filter-status filter-app-name]
                                :or {connect-timeout 5000
                                     read-timeout 10000
                                     retry-attempts 3
                                     retry-delay 1000}})
```

**Parameters:**
- `endpoint` (string, required): MEPM base URL
- `filter-status` (string, optional): Filter by status (e.g., "RUNNING", "STOPPED")
- `filter-app-name` (string, optional): Filter by application name
- Options: Same as `check-health`

**Returns:**
```clojure
{:success? true
 :status 200
 :data {:instances [{:instance-id "app-inst-12345"
                     :app-name "video-analytics"
                     :status "RUNNING"
                     :created-at "2025-10-21T10:30:00Z"}
                    {:instance-id "app-inst-12346"
                     :app-name "face-recognition"
                     :status "RUNNING"
                     :created-at "2025-10-21T09:15:00Z"}]
        :total-count 2}}
```

#### `delete-app-instance`

Terminates and deletes an application instance.

**Signature:**
```clojure
(delete-app-instance endpoint instance-id & {:keys [connect-timeout read-timeout retry-attempts retry-delay
                                                    force]
                                             :or {connect-timeout 5000
                                                  read-timeout 30000
                                                  retry-attempts 3
                                                  retry-delay 1000
                                                  force false}})
```

**Parameters:**
- `endpoint` (string, required): MEPM base URL
- `instance-id` (string, required): Application instance ID
- `force` (boolean, optional): Force deletion even if app is running (default: false)
- Options: Same as `check-health` (note: longer default read-timeout)

**Returns:**
```clojure
{:success? true
 :status 200
 :data {:message "Application instance deleted successfully"
        :instance-id "app-inst-12345"
        :deleted-at "2025-10-21T10:45:00Z"}}
```

---

## Request/Response Formats

### Standard Response Structure

All Mm3 client functions return a standardized response map:

**Success Response:**
```clojure
{:success? true
 :status <http-status-code>
 :data <response-body>}
```

**Error Response:**
```clojure
{:success? false
 :status <http-status-code>
 :error <error-keyword>
 :message <error-message>}
```

### HTTP Status Codes

| Code | Meaning | Description |
|------|---------|-------------|
| 200 | OK | Request successful |
| 201 | Created | Resource created successfully |
| 400 | Bad Request | Invalid request parameters |
| 401 | Unauthorized | Authentication required |
| 403 | Forbidden | Insufficient permissions |
| 404 | Not Found | Resource not found |
| 500 | Internal Server Error | Server error occurred |
| 503 | Service Unavailable | MEPM is degraded or unavailable |

### Error Keywords

| Keyword | Description |
|---------|-------------|
| `:connection-error` | Failed to connect to MEPM |
| `:timeout` | Request timed out |
| `:server-error` | MEPM returned 5xx error |
| `:service-unavailable` | MEPM is degraded (503) |
| `:not-found` | Resource not found (404) |
| `:invalid-request` | Invalid request parameters (400) |
| `:unauthorized` | Authentication failed (401) |
| `:forbidden` | Insufficient permissions (403) |

---

## Error Handling

### Retry Mechanism

All Mm3 client functions implement automatic retry with exponential backoff for transient failures:

```clojure
;; Example: Custom retry configuration
(mm5/check-health "http://mepm.example.com"
                  :retry-attempts 5
                  :retry-delay 2000)  ; 2 seconds between retries
```

**Retry Behavior:**
- Retries on connection errors, timeouts, and 5xx server errors
- Does NOT retry on 4xx client errors (bad request, unauthorized, etc.)
- Exponential backoff: delay × 2^attempt

### Error Handling Patterns

**Pattern 1: Check success flag**
```clojure
(let [response (mm5/check-health endpoint)]
  (if (:success? response)
    (do-something-with (:data response))
    (log/error "Health check failed:" (:message response))))
```

**Pattern 2: Use convenience functions**
```clojure
(when (mm5/healthy? endpoint)
  (deploy-application endpoint app-descriptor))
```

**Pattern 3: Handle specific errors**
```clojure
(let [response (mm5/query-capabilities endpoint)]
  (cond
    (:success? response)
    (process-capabilities (:data response))
    
    (= :timeout (:error response))
    (log/warn "MEPM timeout, will retry later")
    
    (= :service-unavailable (:error response))
    (mark-mepm-degraded mepm-id)
    
    :else
    (log/error "Unexpected error:" (:message response))))
```

---

## Usage Examples

### Example 1: Complete MEPM Health Check Flow

```clojure
(ns my-app.mepm-monitor
  (:require [com.sixsq.nuvla.server.resources.mec.mm3-client :as mm5]
            [clojure.tools.logging :as log]))

(defn monitor-mepm-health
  "Continuously monitor MEPM health and update status"
  [mepm-endpoint mepm-id update-status-fn]
  (let [response (mm5/check-health mepm-endpoint)]
    (if (:success? response)
      (let [status (get-in response [:data :status])]
        (log/info "MEPM" mepm-id "health status:" status)
        (update-status-fn mepm-id status))
      (do
        (log/error "MEPM" mepm-id "health check failed:" (:message response))
        (update-status-fn mepm-id "UNKNOWN")))))
```

### Example 2: Query and Validate Capabilities

```clojure
(defn validate-mepm-capabilities
  "Check if MEPM supports required capabilities"
  [mepm-endpoint required-caps]
  (let [response (mm5/query-capabilities mepm-endpoint)]
    (when (:success? response)
      (let [available-caps (get-in response [:data :capabilities])]
        (every? (set available-caps) required-caps)))))

;; Usage
(validate-mepm-capabilities 
  "http://mepm.example.com"
  ["mec-app-support" "location-services"])
```

### Example 3: Deploy Application with Error Handling

```clojure
(defn deploy-app-to-mepm
  "Deploy application to MEPM with comprehensive error handling"
  [mepm-endpoint app-descriptor]
  (let [response (mm5/create-app-instance 
                   mepm-endpoint 
                   app-descriptor
                   :read-timeout 60000)]  ; 60s for app deployment
    (cond
      (:success? response)
      {:status :success
       :instance-id (get-in response [:data :instance-id])}
      
      (= 400 (:status response))
      {:status :error
       :reason :invalid-descriptor
       :message (:message response)}
      
      (= 503 (:status response))
      {:status :error
       :reason :mepm-unavailable
       :message "MEPM is degraded"}
      
      :else
      {:status :error
       :reason :unknown
       :message (:message response)})))
```

### Example 4: List and Monitor Application Instances

```clojure
(defn monitor-running-apps
  "Monitor all running application instances"
  [mepm-endpoint]
  (let [response (mm5/list-app-instances 
                   mepm-endpoint
                   :filter-status "RUNNING")]
    (when (:success? response)
      (doseq [instance (get-in response [:data :instances])]
        (let [details (mm5/get-app-instance 
                        mepm-endpoint
                        (:instance-id instance))]
          (when (:success? details)
            (log/info "App" (:app-name instance) 
                     "CPU usage:" (get-in details [:data :resources :cpu-usage]) "%")))))))
```

### Example 5: Graceful Application Shutdown

```clojure
(defn shutdown-app-instance
  "Gracefully shutdown and delete application instance"
  [mepm-endpoint instance-id]
  (log/info "Initiating shutdown for app instance" instance-id)
  
  ;; First, check instance status
  (let [status-resp (mm5/get-app-instance mepm-endpoint instance-id)]
    (if (:success? status-resp)
      (do
        (log/info "Current status:" (get-in status-resp [:data :status]))
        
        ;; Attempt graceful deletion
        (let [delete-resp (mm5/delete-app-instance 
                            mepm-endpoint 
                            instance-id
                            :force false)]
          (if (:success? delete-resp)
            (log/info "App instance deleted successfully")
            (do
              (log/warn "Graceful deletion failed, attempting force delete")
              (mm5/delete-app-instance 
                mepm-endpoint 
                instance-id
                :force true)))))
      (log/error "Failed to get instance status:" (:message status-resp)))))
```

---

## MEPM Resource Integration

The Mm3 client is integrated with Nuvla's MEPM resource type. See [MEPM Resource API](mepm-resource-api.md) for:
- MEPM resource CRUD operations
- Action endpoints (check-health, query-capabilities, query-resources)
- Event handling and state management
- Multi-MEPM orchestration patterns

---

## Testing

For testing purposes, use the mock MEPM server:

```clojure
(require '[com.sixsq.nuvla.server.resources.mec.mock-mepm-server :as mock])

;; Start mock server
(mock/start-server! 18081)

;; Test against mock
(mm5/check-health "http://localhost:18081")

;; Simulate errors
(mock/set-error-mode! :timeout)
(mm5/check-health "http://localhost:18081")  ; Will timeout

;; Reset to normal
(mock/set-error-mode! nil)

;; Stop mock server
(mock/stop-server!)
```

See [Test Documentation](testing-guide.md) for comprehensive testing strategies.

---

## See Also

- [ETSI MEC 003 Compliance Matrix](etsi-mec-003-compliance.md)
- [MEPM Resource API](mepm-resource-api.md)
- [Architecture Documentation](architecture.md)
- [Deployment Guide](deployment-guide.md)
