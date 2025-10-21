# MEPM Resource API Reference

## Overview

The MEPM (MEC Platform Manager) resource represents a managed MEC platform in the Nuvla system. This document describes the CRUD operations and actions available on MEPM resources.

## Table of Contents

- [Resource Schema](#resource-schema)
- [CRUD Operations](#crud-operations)
- [Actions](#actions)
- [State Management](#state-management)
- [Usage Examples](#usage-examples)

## Resource Schema

### MEPM Resource Fields

```clojure
{:id "mepm/550e8400-e29b-41d4-a716-446655440000"
 :resource-type "mepm"
 :created "2025-10-21T10:00:00.000Z"
 :updated "2025-10-21T10:30:00.000Z"
 :acl {:owners ["group/nuvla-admin"]
       :view-data ["group/nuvla-user"]}
 
 ;; Required fields
 :endpoint "http://mepm.example.com:8080"
 :name "Production MEPM - Geneva"
 
 ;; Optional fields
 :description "Primary MEPM for Geneva datacenter"
 :status "ONLINE"                    ; ONLINE, DEGRADED, OFFLINE, UNKNOWN
 :last-check "2025-10-21T10:30:00.000Z"
 :capabilities ["mec-app-support"
                "radio-network-information"
                "location-services"]
 :resources {:compute {:cpu-cores 128
                       :memory-gb 512}
             :network {:bandwidth-gbps 100}}
 :platform-info {:platform-id "mepm-prod-01"
                 :version "3.2.1"
                 :vendor "Nuvla"
                 :location {:city "Geneva"
                           :country "Switzerland"}}
 
 ;; Computed fields
 :operations [{:rel "edit" :href "mepm/550e8400-..."}
              {:rel "delete" :href "mepm/550e8400-..."}
              {:rel "check-health" :href "mepm/550e8400-.../check-health"}
              {:rel "query-capabilities" :href "mepm/550e8400-.../query-capabilities"}
              {:rel "query-resources" :href "mepm/550e8400-.../query-resources"}]}
```

### Field Descriptions

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `endpoint` | string | Yes | MEPM base URL (http/https) |
| `name` | string | Yes | Human-readable name |
| `description` | string | No | Detailed description |
| `status` | string | No | Health status (auto-updated by health checks) |
| `last-check` | timestamp | No | Last health check timestamp |
| `capabilities` | array | No | Supported MEC capabilities |
| `resources` | map | No | Available platform resources |
| `platform-info` | map | No | Platform metadata |

### Status Values

| Status | Description |
|--------|-------------|
| `ONLINE` | MEPM is healthy and operational |
| `DEGRADED` | MEPM has reduced functionality |
| `OFFLINE` | MEPM is not reachable |
| `UNKNOWN` | Status has not been checked |

---

## CRUD Operations

### Create MEPM

**Endpoint:** `POST /api/mepm`

**Request Body:**
```json
{
  "endpoint": "http://mepm.example.com:8080",
  "name": "Production MEPM - Geneva",
  "description": "Primary MEPM for Geneva datacenter",
  "capabilities": [
    "mec-app-support",
    "location-services"
  ]
}
```

**Response (201 Created):**
```json
{
  "status": 201,
  "resource-id": "mepm/550e8400-e29b-41d4-a716-446655440000",
  "message": "mepm/550e8400-... created"
}
```

**cURL Example:**
```bash
curl -X POST https://nuvla.example.com/api/mepm \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "endpoint": "http://mepm.example.com:8080",
    "name": "Production MEPM - Geneva"
  }'
```

---

### Read MEPM

**Endpoint:** `GET /api/mepm/{id}`

**Response (200 OK):**
```json
{
  "id": "mepm/550e8400-e29b-41d4-a716-446655440000",
  "resource-type": "mepm",
  "endpoint": "http://mepm.example.com:8080",
  "name": "Production MEPM - Geneva",
  "status": "ONLINE",
  "last-check": "2025-10-21T10:30:00.000Z",
  "capabilities": ["mec-app-support", "location-services"],
  "operations": [
    {"rel": "edit", "href": "mepm/550e8400-..."},
    {"rel": "delete", "href": "mepm/550e8400-..."},
    {"rel": "check-health", "href": "mepm/550e8400-.../check-health"}
  ]
}
```

**cURL Example:**
```bash
curl https://nuvla.example.com/api/mepm/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer $TOKEN"
```

---

### Update MEPM

**Endpoint:** `PUT /api/mepm/{id}`

**Request Body:**
```json
{
  "name": "Production MEPM - Geneva (Updated)",
  "description": "Primary MEPM with enhanced capabilities",
  "capabilities": [
    "mec-app-support",
    "location-services",
    "bandwidth-management"
  ]
}
```

**Response (200 OK):**
```json
{
  "status": 200,
  "message": "mepm/550e8400-... updated"
}
```

**cURL Example:**
```bash
curl -X PUT https://nuvla.example.com/api/mepm/550e8400-e29b-41d4-a716-446655440000 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "Production MEPM - Geneva (Updated)"
  }'
```

---

### Delete MEPM

**Endpoint:** `DELETE /api/mepm/{id}`

**Response (200 OK):**
```json
{
  "status": 200,
  "message": "mepm/550e8400-... deleted"
}
```

**cURL Example:**
```bash
curl -X DELETE https://nuvla.example.com/api/mepm/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer $TOKEN"
```

---

### List MEPMs

**Endpoint:** `GET /api/mepm`

**Query Parameters:**
- `filter`: CDSQL filter expression
- `orderby`: Sort field (e.g., `name:asc`, `created:desc`)
- `first`: Pagination offset (default: 1)
- `last`: Number of results (default: 20)

**Response (200 OK):**
```json
{
  "count": 2,
  "resources": [
    {
      "id": "mepm/550e8400-...",
      "name": "Production MEPM - Geneva",
      "endpoint": "http://mepm.example.com:8080",
      "status": "ONLINE"
    },
    {
      "id": "mepm/660e9511-...",
      "name": "Production MEPM - Paris",
      "endpoint": "http://mepm-paris.example.com:8080",
      "status": "ONLINE"
    }
  ]
}
```

**cURL Examples:**
```bash
# List all MEPMs
curl https://nuvla.example.com/api/mepm \
  -H "Authorization: Bearer $TOKEN"

# Filter by status
curl 'https://nuvla.example.com/api/mepm?filter=status="ONLINE"' \
  -H "Authorization: Bearer $TOKEN"

# Search by name
curl 'https://nuvla.example.com/api/mepm?filter=name^="Production"' \
  -H "Authorization: Bearer $TOKEN"

# Sort by creation date
curl 'https://nuvla.example.com/api/mepm?orderby=created:desc' \
  -H "Authorization: Bearer $TOKEN"
```

---

## Actions

Actions are invoked by POSTing to the action endpoint with an empty body.

### check-health

Performs a health check on the MEPM platform and updates the resource status.

**Endpoint:** `POST /api/mepm/{id}/check-health`

**Request Body:** `{}` (empty JSON object)

**Response (200 OK):**
```json
{
  "status": 200,
  "message": "MEPM health check completed successfully",
  "result": {
    "status": "ONLINE",
    "timestamp": "2025-10-21T10:30:00.000Z",
    "details": "All systems operational"
  }
}
```

**Response (503 Service Unavailable):**
```json
{
  "status": 503,
  "message": "MEPM health check failed: MEPM is degraded"
}
```

**Side Effects:**
- Updates `:status` field in MEPM resource
- Updates `:last-check` timestamp
- May trigger alerts if status changes from ONLINE to DEGRADED

**cURL Example:**
```bash
curl -X POST https://nuvla.example.com/api/mepm/550e8400-.../check-health \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{}'
```

---

### query-capabilities

Retrieves and updates the capabilities supported by the MEPM.

**Endpoint:** `POST /api/mepm/{id}/query-capabilities`

**Request Body:** `{}` (empty JSON object)

**Response (200 OK):**
```json
{
  "status": 200,
  "message": "Capabilities queried successfully",
  "result": {
    "capabilities": [
      "mec-app-support",
      "radio-network-information",
      "location-services",
      "bandwidth-management"
    ],
    "api-version": "2.1.1"
  }
}
```

**Side Effects:**
- Updates `:capabilities` field in MEPM resource

**cURL Example:**
```bash
curl -X POST https://nuvla.example.com/api/mepm/550e8400-.../query-capabilities \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{}'
```

---

### query-resources

Retrieves and updates the available resources on the MEPM.

**Endpoint:** `POST /api/mepm/{id}/query-resources`

**Request Body:** `{}` (empty JSON object)

**Response (200 OK):**
```json
{
  "status": 200,
  "message": "Resources queried successfully",
  "result": {
    "compute": {
      "cpu-cores": 128,
      "memory-gb": 512,
      "storage-gb": 10000
    },
    "network": {
      "bandwidth-gbps": 100,
      "latency-ms": 1
    },
    "accelerators": [
      {
        "type": "GPU",
        "model": "NVIDIA A100",
        "count": 8
      }
    ]
  }
}
```

**Side Effects:**
- Updates `:resources` field in MEPM resource

**cURL Example:**
```bash
curl -X POST https://nuvla.example.com/api/mepm/550e8400-.../query-resources \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{}'
```

---

## State Management

### Status Transitions

```
UNKNOWN (initial)
   ↓ (first health check)
ONLINE ←→ DEGRADED
   ↓ (connection failure)
OFFLINE
   ↓ (successful health check)
ONLINE
```

### Automatic Updates

The following fields are automatically updated by actions:

| Field | Updated By | Frequency |
|-------|------------|-----------|
| `status` | check-health | On-demand or scheduled |
| `last-check` | check-health | Every health check |
| `capabilities` | query-capabilities | On-demand or at registration |
| `resources` | query-resources | On-demand or periodic |

### Status Update Logic

```clojure
;; Simplified status update logic
(defn update-status-from-health-check
  [mepm-id health-response]
  (if (:success? health-response)
    (let [mepm-status (get-in health-response [:data :status])]
      (update-mepm mepm-id
        {:status (if (= "online" mepm-status) "ONLINE" "DEGRADED")
         :last-check (time/now)}))
    (update-mepm mepm-id
      {:status "OFFLINE"
       :last-check (time/now)})))
```

---

## Usage Examples

### Example 1: Register and Initialize MEPM

```clojure
(ns my-app.mepm-setup
  (:require [sixsq.nuvla.client.api :as api]))

;; Create MEPM resource
(def mepm-id
  (-> (api/add "mepm"
               {:endpoint "http://mepm.example.com:8080"
                :name "Production MEPM - Geneva"
                :description "Primary MEPM for Geneva datacenter"})
      :resource-id))

;; Query initial capabilities
(api/operation mepm-id "query-capabilities")

;; Query available resources
(api/operation mepm-id "query-resources")

;; Perform initial health check
(api/operation mepm-id "check-health")
```

### Example 2: Monitor MEPM Health

```clojure
(ns my-app.mepm-monitor
  (:require [sixsq.nuvla.client.api :as api]
            [clojure.tools.logging :as log]))

(defn monitor-all-mepms
  "Monitor health of all registered MEPMs"
  []
  (let [mepms (api/search "mepm")]
    (doseq [mepm (:resources mepms)]
      (try
        (let [result (api/operation (:id mepm) "check-health")]
          (log/info "MEPM" (:name mepm) "status:" 
                   (get-in result [:result :status])))
        (catch Exception e
          (log/error "Failed to check health for" (:name mepm) 
                    ":" (.getMessage e)))))))
```

### Example 3: Find Optimal MEPM for Deployment

```clojure
(defn find-optimal-mepm
  "Find MEPM with required capabilities and sufficient resources"
  [required-capabilities min-cpu min-memory]
  (let [mepms (api/search "mepm" 
                         :filter (str "status='ONLINE'"))]
    (->> (:resources mepms)
         (filter #(every? (set (:capabilities %)) 
                         required-capabilities))
         (filter #(>= (get-in % [:resources :compute :cpu-cores]) min-cpu))
         (filter #(>= (get-in % [:resources :compute :memory-gb]) min-memory))
         (first))))

;; Usage
(def mepm (find-optimal-mepm 
            ["mec-app-support" "location-services"]
            16    ; min 16 CPU cores
            32))  ; min 32 GB memory
```

### Example 4: Update MEPM Configuration

```clojure
(defn update-mepm-metadata
  "Update MEPM metadata after platform upgrade"
  [mepm-id]
  ;; Update basic info
  (api/edit mepm-id
           {:description "Upgraded to v3.3.0 with enhanced features"})
  
  ;; Refresh capabilities (may have changed after upgrade)
  (api/operation mepm-id "query-capabilities")
  
  ;; Refresh resources (may have new hardware)
  (api/operation mepm-id "query-resources")
  
  ;; Verify health
  (api/operation mepm-id "check-health"))
```

### Example 5: Decommission MEPM

```clojure
(defn decommission-mepm
  "Safely decommission a MEPM"
  [mepm-id]
  (log/info "Starting decommissioning process for" mepm-id)
  
  ;; 1. Get current MEPM info
  (let [mepm (api/get mepm-id)]
    (log/info "Decommissioning MEPM:" (:name mepm))
    
    ;; 2. Check for running applications (would need app-instance resource)
    ;; (ensure-no-running-apps mepm-id)
    
    ;; 3. Mark as offline
    (api/edit mepm-id {:status "OFFLINE"})
    
    ;; 4. Delete resource
    (api/delete mepm-id)
    
    (log/info "MEPM decommissioned successfully")))
```

---

## Integration with Mm5 Client

MEPM resource actions internally use the Mm5 client library:

```clojure
(ns com.sixsq.nuvla.server.resources.mepm
  (:require [com.sixsq.nuvla.server.resources.mec.mm5-client :as mm5]))

(defmethod crud/do-action [resource-type "check-health"]
  [{{:keys [id]} :body :as request}]
  (let [mepm (crud/retrieve-by-id-as-admin id)
        endpoint (:endpoint mepm)
        response (mm5/check-health endpoint)]
    
    (if (:success? response)
      ;; Update MEPM resource with successful health check
      (let [status (get-in response [:data :status])]
        (crud/edit {:params {:uuid (u/id->uuid id)}
                   :body {:status (if (= "online" status) "ONLINE" "DEGRADED")
                          :last-check (time/now)}}))
      ;; Mark as offline on failure
      (crud/edit {:params {:uuid (u/id->uuid id)}
                 :body {:status "OFFLINE"
                        :last-check (time/now)}}))
    
    ;; Return response to caller
    response))
```

---

## See Also

- [Mm5 API Reference](mm5-api-reference.md) - Low-level Mm5 client functions
- [ETSI MEC 003 Compliance](etsi-mec-003-compliance.md) - Standards compliance
- [Architecture Documentation](architecture.md) - System architecture
- [Testing Guide](testing-guide.md) - Testing strategies
