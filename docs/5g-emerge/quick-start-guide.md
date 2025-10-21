# MEC MEO Implementation - Quick Start Guide

## Overview

This guide provides quick setup instructions for the MEC MEO (Multi-access Edge Orchestrator) implementation with MEPM (MEC Platform Manager) integration via the Mm5 interface.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Development Setup](#development-setup)
- [Production Deployment](#production-deployment)
- [Testing](#testing)
- [Configuration](#configuration)

---

## Prerequisites

### Required
- **Clojure** 1.11.0 or later
- **Leiningen** 2.9.0 or later
- **Java** 21 or later
- **Elasticsearch** 7.x or later (for Nuvla backend)

### Optional
- **Docker** (for containerized deployment)
- **MEPM Platform** (for production use)

---

## Development Setup

### 1. Clone Repository

```bash
git clone https://github.com/nuvla/api-server.git
cd api-server/code
```

### 2. Install Dependencies

```bash
lein deps
```

### 3. Start Development Environment

```bash
# Start Elasticsearch (if not running)
docker run -d -p 9200:9200 -e "discovery.type=single-node" elasticsearch:7.17.0

# Start Nuvla API server
lein repl
```

### 4. Run Tests

```bash
# Run all MEC tests
lein test :only com.sixsq.nuvla.server.resources.mec.*

# Run specific test suites
lein test com.sixsq.nuvla.server.resources.mec.mm5-integration-test
lein test com.sixsq.nuvla.server.resources.mec.orchestration-test
lein test com.sixsq.nuvla.server.resources.mepm-lifecycle-test
```

---

## Production Deployment

### 1. MEPM Registration

Register a production MEPM platform:

```bash
curl -X POST https://nuvla.example.com/api/mepm \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "endpoint": "https://mepm.example.com:8080",
    "name": "Production MEPM - Geneva",
    "description": "Primary MEPM for Geneva datacenter"
  }'
```

### 2. Initialize MEPM

Query capabilities and resources:

```bash
# Get MEPM ID from registration response
MEPM_ID="mepm/550e8400-e29b-41d4-a716-446655440000"

# Query capabilities
curl -X POST https://nuvla.example.com/api/$MEPM_ID/query-capabilities \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{}'

# Query resources
curl -X POST https://nuvla.example.com/api/$MEPM_ID/query-resources \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{}'

# Health check
curl -X POST https://nuvla.example.com/api/$MEPM_ID/check-health \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{}'
```

### 3. Configure Health Monitoring

Set up periodic health checks (example cron job):

```bash
#!/bin/bash
# health-monitor.sh

TOKEN="your-auth-token"
MEPM_ID="mepm/550e8400-e29b-41d4-a716-446655440000"

curl -X POST https://nuvla.example.com/api/$MEPM_ID/check-health \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{}' \
  -s | jq '.result.status'
```

Add to crontab for every 5 minutes:
```bash
*/5 * * * * /path/to/health-monitor.sh >> /var/log/mepm-health.log 2>&1
```

---

## Testing

### Mock MEPM Server

For development and testing, use the built-in mock MEPM server:

```clojure
(require '[com.sixsq.nuvla.server.resources.mec.mock-mepm-server :as mock])

;; Start mock server on port 18081
(mock/start-server! 18081)

;; Register mock MEPM in Nuvla
;; Use endpoint: "http://localhost:18081"

;; Simulate error scenarios
(mock/set-error-mode! :timeout)      ; Simulate timeout
(mock/set-error-mode! :server-error) ; Simulate 500 error
(mock/set-error-mode! :degraded)     ; Simulate degraded MEPM
(mock/set-error-mode! nil)           ; Reset to normal

;; Stop mock server
(mock/stop-server!)
```

### Test Suites

#### 1. Mm5 Integration Tests (21 tests, 78 assertions)

Tests all Mm5 operations against mock MEPM:

```bash
lein test com.sixsq.nuvla.server.resources.mec.mm5-integration-test
```

**Coverage:**
- Health monitoring (4 tests)
- Capability queries (2 tests)
- Resource queries (2 tests)
- Platform info (1 test)
- Configuration (1 test)
- Error handling (4 tests)
- App lifecycle (2 tests)
- Concurrent access (2 tests)
- End-to-end flows (3 tests)

#### 2. Orchestration Tests (3 tests, 25 assertions)

Tests complete MEPM orchestration flows:

```bash
lein test com.sixsq.nuvla.server.resources.mec.orchestration-test
```

**Coverage:**
- Complete MEPM orchestration flow (registration → operations → deletion)
- Multiple MEPM management
- Health degradation and recovery

#### 3. MEPM Lifecycle Tests (2 tests, 35 assertions)

Tests MEPM resource lifecycle:

```bash
lein test com.sixsq.nuvla.server.resources.mepm-lifecycle-test
```

**Coverage:**
- CRUD operations
- Action invocations
- Status transitions

### Running All Tests

```bash
# Run all MEC-related tests
lein test :only com.sixsq.nuvla.server.resources.mec.*

# With coverage report
lein cloverage -n 'com.sixsq.nuvla.server.resources.mec.*'
```

**Expected Results:**
- Total: 26 tests
- Assertions: 138
- Failures: 0
- Errors: 0

---

## Configuration

### Mm5 Client Options

All Mm5 client functions accept optional configuration:

```clojure
(require '[com.sixsq.nuvla.server.resources.mec.mm5-client :as mm5])

(mm5/check-health "https://mepm.example.com"
                  :connect-timeout 5000    ; Connection timeout (ms)
                  :read-timeout 10000      ; Read timeout (ms)
                  :retry-attempts 3        ; Number of retries
                  :retry-delay 1000)       ; Delay between retries (ms)
```

### MEPM Resource Configuration

Recommended MEPM resource fields for production:

```json
{
  "endpoint": "https://mepm.example.com:8080",
  "name": "Production MEPM - Geneva",
  "description": "Primary MEPM for Geneva datacenter with high availability",
  "capabilities": [
    "mec-app-support",
    "radio-network-information",
    "location-services",
    "bandwidth-management"
  ],
  "platform-info": {
    "location": {
      "city": "Geneva",
      "country": "Switzerland",
      "coordinates": {
        "lat": 46.2044,
        "lon": 6.1432
      }
    },
    "contact": {
      "email": "mec-ops@example.com",
      "phone": "+41-22-xxx-xxxx"
    }
  }
}
```

### Environment Variables

Configure via environment or `profiles.clj`:

```clojure
;; ~/.lein/profiles.clj
{:user
 {:env {:mepm-default-timeout "10000"
        :mepm-retry-attempts "3"
        :mepm-health-check-interval "300"}}}  ; 5 minutes
```

---

## Architecture Overview

### Components

```
┌─────────────┐
│   Nuvla     │
│ MEO Service │
│             │
│  ┌──────┐   │      Mm5 Interface
│  │ MEPM │───┼────────────────────────┐
│  │Resource  │                        │
│  └──────┘   │                        │
│      │      │                        ▼
│  ┌──────┐   │                 ┌──────────┐
│  │ Mm5  │   │                 │   MEPM   │
│  │Client│───┼────────────────▶│ Platform │
│  └──────┘   │  HTTP/JSON      └──────────┘
└─────────────┘
```

### Key Flows

#### 1. MEPM Registration

```
User → POST /api/mepm → Create MEPM Resource
                      ↓
               Query Capabilities
                      ↓
               Query Resources
                      ↓
               Check Health
                      ↓
               Update MEPM Resource
```

#### 2. Health Monitoring

```
Scheduler → POST /api/mepm/{id}/check-health
                      ↓
                Mm5 Client
                      ↓
            GET /mm5/health (MEPM)
                      ↓
            Update MEPM Status
                      ↓
            Generate Alerts (if status changed)
```

---

## Troubleshooting

### Issue: Connection Timeout

**Symptoms:** Health checks fail with timeout errors

**Solutions:**
1. Increase timeout values:
   ```clojure
   (mm5/check-health endpoint :connect-timeout 10000 :read-timeout 30000)
   ```
2. Check network connectivity to MEPM
3. Verify MEPM is running and accessible

### Issue: MEPM Status Shows UNKNOWN

**Symptoms:** MEPM status remains UNKNOWN after creation

**Solutions:**
1. Manually trigger health check:
   ```bash
   curl -X POST https://nuvla.example.com/api/$MEPM_ID/check-health \
     -H "Authorization: Bearer $TOKEN" -d '{}'
   ```
2. Verify MEPM endpoint is correct
3. Check MEPM logs for errors

### Issue: Test Failures

**Symptoms:** Integration tests fail randomly

**Solutions:**
1. Ensure Elasticsearch is running:
   ```bash
   curl http://localhost:9200
   ```
2. Stop any existing mock servers:
   ```clojure
   (mock/stop-server!)
   ```
3. Run tests in isolation:
   ```bash
   lein test com.sixsq.nuvla.server.resources.mec.mm5-integration-test
   ```

---

## Next Steps

1. **Read API Documentation**
   - [Mm5 API Reference](mm5-api-reference.md)
   - [MEPM Resource API](mepm-resource-api.md)

2. **Review Compliance**
   - [ETSI MEC 003 Compliance Matrix](etsi-mec-003-compliance.md)

3. **Explore Examples**
   - See "Usage Examples" sections in API reference docs
   - Review test files for integration patterns

4. **Production Checklist**
   - [ ] MEPM endpoint uses HTTPS
   - [ ] Authentication configured
   - [ ] Health monitoring scheduled
   - [ ] Alerting configured for status changes
   - [ ] Backup MEPMs registered for high availability
   - [ ] Logs aggregated and monitored

---

## Support

For issues or questions:
- GitHub Issues: https://github.com/nuvla/api-server/issues
- Documentation: `/docs/5g-emerge/`
- ETSI MEC Specifications: https://www.etsi.org/technologies/multi-access-edge-computing

---

## Version Information

- **Implementation Version:** Nuvla API Server 5g-emerge branch
- **ETSI MEC 003 Version:** V3.1.1 (2022-03)
- **Last Updated:** 2025-10-21
