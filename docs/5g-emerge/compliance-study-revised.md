# Compliance Study of Nuvla Edge Orchestration Solution
## Assessment of Compliance with ETSI MEC Standards

**Version:** 2.0  
**Date:** October 2025  
**Standard References:**
- ETSI GS MEC 003 v3.1.1 - Framework and Reference Architecture
- ETSI GS MEC 010-2 v2.2.1 - Application Lifecycle Management API
- ETSI GS MEC 002 v2.2.1 - Technical Requirements

**Scope:** Minimum Viable MEC Orchestrator (MEO) Compliance

---

## Executive Summary

This document assesses Nuvla's compliance with ETSI MEC standards for operating as a **MEC Orchestrator (MEO)**. The MEO is the core system-level management component in a Multi-access Edge Computing (MEC) architecture, responsible for orchestrating application lifecycle across multiple edge hosts.

**Target Scope:** Minimum Viable MEO (Phase 1 + Phase 2)
- Focus on core orchestration capabilities
- MEO-level only (excludes MEPM/MEP implementation)
- Production-ready baseline functionality

**Compliance Approach:**
1. Define all MEO responsibilities per ETSI MEC 003 §6.2
2. Map to specific technical requirements (MEC 010-2 API)
3. Identify implementation gaps with severity assessment
4. Provide phased implementation roadmap
5. Define minimum requirements for MECwiki registration

---

## 1. MEC Orchestrator (MEO) Definition

According to ETSI GS MEC 003 v3.1.1 §6.2.1, the MEC Orchestrator (MEO) is defined as:

> "The MEO is the core functionality in the MEC system level management. The MEO takes care of the orchestration of MEC applications and the related lifecycles, the orchestration of the resource management for the applications, and the inter-system communication."

### 1.1 Core MEO Responsibilities

The MEO has the following responsibilities, organized by priority for minimum viable implementation:

#### **Priority 1: Critical (Minimum Viable MEO)**

**R1.1 - Application Lifecycle Management (MEC 003 §6.2.1.3)**
- Trigger application instantiation (deployment)
- Trigger application termination (undeployment)
- Manage application operational state (start, stop, restart)
- Query application instances and their states
- Track operation history and status

**R1.2 - MEO-MEPM Communication Interface (MEC 003 §6.3)**
- Communicate with MEC Platform Managers (MEPM) via Mm5 reference point
- Query MEPM capabilities (supported platforms, services)
- Query MEPM resource availability (CPU, memory, GPU, storage)
- Delegate application deployment to selected MEPM
- Monitor MEPM health and status

**R1.3 - Host Selection for App Instantiation (MEC 003 §6.2.1.3)**
- Select appropriate MEC host(s) based on resource constraints
- Match application requirements with available resources
- Basic placement algorithm (first-fit with resource filtering)
- Handle placement failures gracefully

#### **Priority 2: Important (Production MEO)**

**R2.1 - Application Package Management (MEC 003 §6.2.1.2)**
- On-board application packages with metadata
- Store application descriptors and artifacts
- Keep record of on-boarded packages with versioning
- Provide application catalog/registry
- Support multiple package formats (Docker, Kubernetes)

**R2.2 - Package Integrity & Authenticity (MEC 003 §6.2.1.2)**
- Check integrity of application packages (checksums, signatures)
- Verify authenticity of packages (digital signatures, certificates)
- Validate package sources (trusted registries)
- Detect tampering and unauthorized modifications

**R2.3 - Policy Validation & Enforcement (MEC 003 §6.2.1.2)**
- Validate application rules and requirements
- Enforce operator policies (resource limits, security policies)
- Adjust application configurations to comply with policies
- Reject non-compliant applications with clear error messages

**R2.4 - MEC System Overview (MEC 003 §6.2.1.1)**
- Maintain inventory of deployed MEC hosts
- Track available resources across all hosts
- Monitor available MEC services per host
- Maintain topology information (network connectivity)

**R2.5 - Event Subscriptions & Notifications (MEC 010-2 §7.3)**
- Support subscriptions to application lifecycle events
- Deliver notifications on state changes via webhooks
- Support filtering of notifications by criteria
- Retry failed notification deliveries

**R2.6 - Operation Tracking & History (MEC 010-2 §7.2.4)**
- Record all lifecycle operations with timestamps
- Track operation states (starting, processing, completed, failed)
- Provide queryable operation history
- Calculate operation statistics (success rate, duration)

**R2.7 - Error Handling & Reporting (RFC 7807, MEC 010-2 §6.15)**
- Provide machine-readable error responses
- Follow RFC 7807 ProblemDetails format
- Include MEC-specific error context
- Support error recovery and troubleshooting

#### **Priority 3: Advanced (Future Enhancements)**

**R3.1 - Advanced Host Selection (MEC 003 §6.2.1.3)**
- Multi-criteria placement optimization (latency, cost, load)
- Service-aware placement (check required MEC services)
- Affinity/anti-affinity rules for co-location
- Geographic and regulatory constraints
- Load balancing across hosts

**R3.2 - Application Relocation (MEC 003 §6.2.1.4)**
- Trigger application relocation when needed
- Automatic relocation based on triggers (resource exhaustion, performance)
- Support for stateful application migration
- Traffic management during relocation (DNS, load balancer)
- Rollback on relocation failure

**R3.3 - Multi-MEPM Coordination**
- Manage multiple MEPM instances
- MEPM discovery and registration
- Load balancing across MEPMs
- Failover when MEPM unavailable

**R3.4 - Network Topology Awareness**
- Map network topology (connectivity, latency)
- Latency-aware placement decisions
- Service discovery across hosts
- Dynamic topology updates

**R3.5 - Fault Management & Recovery**
- Detect host and MEPM failures
- Automatic application recovery (restart or relocate)
- Graceful degradation on failures
- Rollback on deployment failures

---

## 2. MEC 010-2 API Requirements

For the MEO to be compliant with ETSI MEC 010-2, it must implement the Application Lifecycle Management API.

### 2.1 Required API Endpoints (Minimum Viable)

#### **App Instance Management (4 endpoints)**

| Endpoint | Method | Purpose | MEC 010-2 Reference |
|----------|--------|---------|---------------------|
| `/app_lcm/v2/app_instances` | POST | Create app instance | §7.2.2.3.1 |
| `/app_lcm/v2/app_instances` | GET | Query app instances | §7.2.2.3.2 |
| `/app_lcm/v2/app_instances/{id}` | GET | Get individual app instance | §7.2.2.3.3 |
| `/app_lcm/v2/app_instances/{id}` | DELETE | Delete app instance | §7.2.2.3.4 |

#### **Lifecycle Operations (3 endpoints)**

| Endpoint | Method | Purpose | MEC 010-2 Reference |
|----------|--------|---------|---------------------|
| `/app_lcm/v2/app_instances/{id}/instantiate` | POST | Deploy application | §7.2.3.3.1 |
| `/app_lcm/v2/app_instances/{id}/terminate` | POST | Undeploy application | §7.2.3.3.2 |
| `/app_lcm/v2/app_instances/{id}/operate` | POST | Start/stop application | §7.2.3.3.3 |

#### **Operation Occurrence Tracking (2 endpoints)**

| Endpoint | Method | Purpose | MEC 010-2 Reference |
|----------|--------|---------|---------------------|
| `/app_lcm/v2/app_lcm_op_occs` | GET | Query operations history | §7.2.4.3.2 |
| `/app_lcm/v2/app_lcm_op_occs/{id}` | GET | Get operation details | §7.2.4.3.3 |

**Total Required: 9 endpoints**

### 2.2 Optional API Endpoints (Production Readiness)

#### **Subscription Management (4 endpoints)**

| Endpoint | Method | Purpose | MEC 010-2 Reference |
|----------|--------|---------|---------------------|
| `/app_lcm/v2/subscriptions` | POST | Create subscription | §7.3.2.3.1 |
| `/app_lcm/v2/subscriptions` | GET | Query subscriptions | §7.3.2.3.2 |
| `/app_lcm/v2/subscriptions/{id}` | GET | Get subscription | §7.3.2.3.3 |
| `/app_lcm/v2/subscriptions/{id}` | DELETE | Delete subscription | §7.3.2.3.4 |

**Total Optional: 4 endpoints**

**Minimum Viable MEO: 9 endpoints (required only)**  
**Production MEO: 13 endpoints (required + optional)**

### 2.3 Required Data Models

| Data Model | Purpose | MEC 010-2 Reference |
|------------|---------|---------------------|
| **AppInstanceInfo** | Application instance resource | §6.2.1.2 |
| **InstantiateAppRequest** | Instantiation parameters | §6.2.1.4 |
| **TerminateAppRequest** | Termination parameters | §6.2.1.5 |
| **OperateAppRequest** | Operation parameters (start/stop) | §6.2.1.6 |
| **AppLcmOpOcc** | Operation occurrence (history) | §6.2.1.8 |
| **AppInstanceSubscriptionInfo** | Subscription resource | §6.2.1.11 |
| **AppInstanceNotification** | Notification message | §6.2.1.12 |
| **ProblemDetails** | Error response (RFC 7807) | §6.15 |

### 2.4 State Management

#### **Instantiation State (required)**
- `NOT_INSTANTIATED` - App instance exists but not deployed
- `INSTANTIATED` - App instance deployed and running

#### **Operational State (required when instantiated)**
- `STARTED` - Application is running
- `STOPPED` - Application is stopped
- `UNKNOWN` - State cannot be determined

### 2.5 Additional Requirements

**Query & Filtering (MEC 010-2 §4.4)**
- Support attribute-based filtering
- Pagination for large result sets
- Field selection to reduce payload size

**HATEOAS Navigation (MEC 010-2 §4.4)**
- `_links` in all resources
- Self, next, prev links
- Action links (instantiate, terminate, operate)

**Error Handling (MEC 010-2 §6.15)**
- RFC 7807 ProblemDetails format
- Consistent error types and status codes
- Detailed error messages with context

---

## 3. Mm5 Reference Point Requirements

The Mm5 interface connects the MEO to MEC Platform Managers (MEPM). Per ETSI MEC 003 §6.3, this is a critical MEO interface.

### 3.1 Required Mm5 Operations

| Operation | Purpose | MEPM Endpoint Example |
|-----------|---------|----------------------|
| **Health Check** | Verify MEPM availability | `GET /health` |
| **Query Capabilities** | Get supported platforms/services | `GET /capabilities` |
| **Query Resources** | Get available resources | `GET /resources` |
| **Deploy App** | Request app deployment | `POST /app_instances` |
| **Query App Status** | Get deployment status | `GET /app_instances/{id}` |
| **Terminate App** | Request app termination | `DELETE /app_instances/{id}` |

### 3.2 Mm5 Client Requirements

**Technical Implementation:**
- HTTP/HTTPS client with TLS support
- Authentication (OAuth2, API key, mTLS)
- Retry logic with exponential backoff
- Connection pooling for performance
- Timeout configuration
- Error handling and logging

**MEPM Selection:**
- Support multiple MEPM backends
- MEPM discovery and registration
- Selection algorithm (first-fit minimum)
- Failover to alternate MEPM on failure

---

## 4. Gap Analysis

This section identifies implementation gaps for achieving minimum viable MEO compliance.

### 4.1 Critical Gaps (Block MEO Viability)

#### **Gap 1: MEC 010-2 API Implementation**

**Requirement:** ETSI GS MEC 010-2 v2.2.1 (entire standard)

**Current State:** ❌ **NOT IMPLEMENTED**

**Required Implementation:**
1. **9 REST API endpoints** (app instance, lifecycle, operations)
   - POST /app_instances (create)
   - GET /app_instances (query with filtering)
   - GET /app_instances/{id} (get details)
   - DELETE /app_instances/{id} (delete)
   - POST /app_instances/{id}/instantiate
   - POST /app_instances/{id}/terminate
   - POST /app_instances/{id}/operate
   - GET /app_lcm_op_occs (query operations)
   - GET /app_lcm_op_occs/{id} (get operation)

2. **Data models with validation**
   - AppInstanceInfo (with all required fields)
   - InstantiateAppRequest, TerminateAppRequest, OperateAppRequest
   - AppLcmOpOcc (operation tracking)
   - ProblemDetails (error responses)

3. **State management**
   - Instantiation state machine (NOT_INSTANTIATED ↔ INSTANTIATED)
   - Operational state machine (STARTED, STOPPED, UNKNOWN)
   - State transition validation

4. **Query capabilities**
   - Attribute-based filtering (FIQL or similar)
   - Pagination with HAL links
   - Field selection

5. **HATEOAS navigation**
   - _links in all resources
   - Self, instantiate, terminate, operate links
   - Pagination links (next, prev, first, last)

**Implementation Effort:** 4-6 weeks (1 developer)

**Priority:** 🔴 **CRITICAL** - Blocks MEO certification

**Testing Requirements:**
- 100+ unit tests covering all endpoints
- Integration tests for end-to-end flows
- Error scenario testing
- Performance testing (response times, throughput)

---

#### **Gap 2: Mm5 Interface (MEO-MEPM Communication)**

**Requirement:** ETSI GS MEC 003 §6.3

**Current State:** ❌ **NOT IMPLEMENTED**

**Required Implementation:**
1. **Mm5 HTTP Client Library**
   - Health check operation
   - Capability query (platforms, services, API version)
   - Resource query (CPU, memory, GPU, storage)
   - App deployment request
   - App status query
   - App termination request

2. **Reliability Features**
   - Retry logic with exponential backoff
   - Connection pooling
   - Configurable timeouts
   - Circuit breaker pattern for failed MEPMs

3. **MEPM Management**
   - MEPM registration/discovery
   - MEPM health monitoring
   - Multi-MEPM support (select among available)
   - Failover to backup MEPM

4. **Error Handling**
   - MEPM connection failures
   - MEPM unavailable errors
   - Deployment failures with rollback
   - Timeout handling

**Implementation Effort:** 2-3 weeks (1 developer)

**Priority:** 🔴 **CRITICAL** - Required for MEO operation

**Testing Requirements:**
- Mock MEPM server for deterministic testing
- All 5+ operations tested
- Error scenarios (timeout, connection failure, etc.)
- Retry logic validation

**Integration Notes:**
- Lifecycle handler delegates to Mm5 client for deployment
- Selection algorithm chooses MEPM based on capabilities/resources

---

#### **Gap 3: Basic Host Selection / Placement Algorithm**

**Requirement:** ETSI GS MEC 003 §6.2.1.3

**Current State:** ❌ **NOT IMPLEMENTED**

**Required Implementation:**
1. **Resource-Based Placement (Minimum Viable)**
   ```
   Algorithm: First-Fit with Resource Filtering
   
   Input: App requirements (CPU, memory, GPU, storage)
   
   For each MEPM in registry:
     Query MEPM capabilities (supported platforms)
     If platform_match(app, MEPM):
       Query MEPM resources (available CPU, memory, etc.)
       If resources_sufficient(app_requirements, available_resources):
         Select this MEPM
         Return success
   
   Return placement_failed (no suitable MEPM found)
   ```

2. **Placement Validation**
   - Validate app requirements are specified
   - Check at least one MEPM available
   - Verify selected MEPM is healthy
   - Handle placement failures gracefully

3. **Placement Configuration**
   - Configurable placement strategy (default: first-fit)
   - Resource overcommit thresholds (optional)
   - Reserved resources per MEPM (optional)

**Implementation Effort:** 1-2 weeks (1 developer)

**Priority:** 🔴 **CRITICAL** - Required for deployment

**Testing Requirements:**
- Test with single MEPM
- Test with multiple MEPMs (picks first suitable)
- Test with insufficient resources (fails gracefully)
- Test with unavailable MEPM (skips to next)

**Future Enhancements (Phase 3):**
- Multi-criteria scoring (latency, cost, load)
- Service-aware placement (check required MEC services)
- Affinity/anti-affinity rules
- Load balancing across MEPMs

---

#### **Gap 4: Operation Occurrence Tracking**

**Requirement:** ETSI GS MEC 010-2 §7.2.4

**Current State:** ❌ **NOT IMPLEMENTED**

**Required Implementation:**
1. **Operation Recording**
   - Record every lifecycle operation (instantiate, terminate, operate)
   - Include: operation-id, app-instance-id, operation-type, start-time, end-time
   - Track operation state (STARTING, PROCESSING, COMPLETED, FAILED)
   - Store operation parameters and results

2. **AppLcmOpOcc Data Model**
   - Operation occurrence resource per MEC 010-2 §6.2.1.8
   - All required fields (id, operationState, stateEnteredTime, etc.)
   - Links to related app instance

3. **Query Operations**
   - GET /app_lcm_op_occs endpoint
   - Filter by: app-instance-id, operation-type, operation-state
   - Time range filtering (start-time-after, start-time-before)
   - Pagination support

4. **Persistence**
   - Store operations in database (survive restarts)
   - Retention policy (keep for X days, configurable)
   - Efficient indexing for queries

**Implementation Effort:** 2-3 weeks (1 developer)

**Priority:** 🔴 **CRITICAL** - Required for MEC 010-2 compliance

**Testing Requirements:**
- Record operations correctly
- Query by various filters
- Pagination works correctly
- Historical data survives restarts

**Implementation Approach:**
- Leverage existing Nuvla job system for persistence
- Map job states to operation states
- Wrap lifecycle operations to automatically record

---

### 4.2 Important Gaps (Limit Production Use)

#### **Gap 5: Package Integrity & Authenticity (DCT)**

**Requirement:** ETSI GS MEC 003 §6.2.1.2

**Current State:** ⚠️ **PARTIAL** - Nuvla stores packages, but no integrity/signature checking

**Required Implementation:**
1. **Docker Content Trust (DCT)**
   - Enable DCT in Docker environment
   - Configure trust policy (require signed images, warn, audit)
   - Integrate with image pull process
   - Reject unsigned images per policy

2. **Notary Service Deployment**
   - Deploy Docker Notary service for signature storage
   - Configure trust roots and signing keys
   - Certificate lifecycle management
   - Key rotation procedures

3. **Kubernetes Manifest Signing**
   - Use cosign or similar for K8s manifest signatures
   - Verify manifests before deployment
   - Policy for unsigned manifests

4. **OCI Artifact Signatures**
   - Support for OCI artifact signatures (emerging standard)
   - Verification workflow
   - Trust root configuration

**Implementation Effort:** 3-4 weeks (1 developer + infrastructure setup)

**Priority:** 🟡 **HIGH** - Security concern, but can operate without initially

**Testing Requirements:**
- Test signed image acceptance
- Test unsigned image rejection
- Test invalid signature rejection
- Test certificate expiration scenarios

**Feasibility:** ✅ **Proven** - DCT plugin for Docker already partially implemented

---

#### **Gap 6: Policy Validation & Enforcement (OPA)**

**Requirement:** ETSI GS MEC 003 §6.2.1.2

**Current State:** ⚠️ **PARTIAL** - Nuvla validates basic requirements, but no policy engine

**Required Implementation:**
1. **Open Policy Agent (OPA) Integration**
   - Deploy OPA server or sidecar
   - Define policy language (Rego)
   - Integrate with deployment workflow

2. **Policy Categories**
   - **Resource policies:** Max CPU, memory, storage per app
   - **Security policies:**
     - No privileged containers
     - No host network/PID namespace access
     - Required security contexts (runAsNonRoot, etc.)
     - Image source validation (allowed registries)
   - **Network policies:**
     - Allowed ports and protocols
     - Egress restrictions
     - Service mesh requirements
   - **Compliance policies:**
     - Data residency requirements
     - Regulatory compliance tags
     - License compatibility checks
   - **Operational policies:**
     - Required labels and annotations
     - Naming conventions
     - Backup/HA requirements

3. **Policy Enforcement Workflow**
   ```
   1. User submits app deployment request
   2. Extract app descriptor (IaC)
   3. Send to OPA for policy evaluation
   4. OPA returns: allow/deny + violations
   5. If deny: Return RFC 7807 error with violations
   6. If allow: Proceed with deployment
   7. Optionally: Adjust config to comply (e.g., add resource limits)
   ```

4. **Policy Management**
   - Policy versioning and updates
   - Policy testing framework
   - Policy documentation
   - Override mechanisms for admins

**Implementation Effort:** 3-4 weeks (1 developer)

**Priority:** 🟡 **HIGH** - Important for operator control, can operate without initially

**Testing Requirements:**
- Test policy allow scenarios
- Test policy deny scenarios
- Test policy violation messages
- Performance testing (policy eval overhead)

**Feasibility:** ✅ **Proven** - Feasibility study completed, confirmed OPA works with Nuvla IaC

---

#### **Gap 7: RFC 7807 Error Handling**

**Requirement:** ETSI GS MEC 010-2 §6.15 + RFC 7807

**Current State:** ⚠️ **PARTIAL** - Nuvla returns errors, but not RFC 7807 format

**Required Implementation:**
1. **ProblemDetails Data Model**
   ```json
   {
     "type": "https://docs.nuvla.io/mec/errors/not-found",
     "title": "Resource Not Found",
     "status": 404,
     "detail": "Application instance app-123 not found",
     "instance": "/app_lcm/v2/app_instances/app-123"
   }
   ```

2. **Error Types (13 minimum)**
   - **4xx Client Errors:**
     - bad-request (400)
     - unauthorized (401)
     - forbidden (403)
     - not-found (404)
     - method-not-allowed (405)
     - conflict (409)
     - gone (410)
     - validation-failed (422)
   - **5xx Server Errors:**
     - internal-server-error (500)
     - not-implemented (501)
     - bad-gateway (502)
     - service-unavailable (503)
     - mepm-error (custom 502)

3. **MEC-Specific Extensions**
   - `current-state`: Current resource state
   - `expected-state`: Expected state for operation
   - `operation`: Operation that failed
   - `mepm-endpoint`: MEPM that returned error

4. **Error Type URIs**
   - Host error type URIs at: `https://docs.nuvla.io/mec/errors/{type}`
   - Provide human-readable error documentation
   - Include recovery suggestions

**Implementation Effort:** 1-2 weeks (1 developer)

**Priority:** 🟡 **MEDIUM** - Important for standards compliance and debugging

**Testing Requirements:**
- Test all error types
- Validate JSON schema compliance
- Test error context fields
- Client parsing of errors

---

#### **Gap 8: Subscription & Notification System**

**Requirement:** ETSI GS MEC 010-2 §7.3 (optional but valuable)

**Current State:** ⚠️ **PARTIAL** - Nuvla has event system, but not MEC 010-2 compliant

**Required Implementation:**
1. **Subscription API (4 endpoints)**
   - POST /subscriptions (create)
   - GET /subscriptions (query)
   - GET /subscriptions/{id} (get)
   - DELETE /subscriptions/{id} (delete)

2. **Subscription Data Model**
   - Subscription filters:
     - app-name, app-instance-id
     - operational-state (STARTED, STOPPED)
     - instantiation-state (INSTANTIATED)
     - operation-type (INSTANTIATE, TERMINATE, OPERATE)
     - operation-state (COMPLETED, FAILED)
   - Callback URI for webhook delivery
   - Subscription owner/permissions

3. **Notification Types**
   - **AppInstanceStateChangeNotification**
     - Sent when app state changes (STARTED → STOPPED, etc.)
     - Includes: app-instance-id, new state, old state, timestamp
   - **AppLcmOpOccStateChangeNotification**
     - Sent when operation completes/fails
     - Includes: operation-id, operation-type, state, result

4. **Notification Delivery**
   - Webhook HTTP POST to callback URI
   - Retry logic (3 attempts, exponential backoff: 2s, 4s, 8s)
   - Delivery tracking (success, failure, retry count)
   - Timeout handling (30s default)
   - Async non-blocking delivery

5. **Filter Matching**
   - Evaluate filters on each event
   - Match any criteria (OR logic within filter)
   - Support multiple subscriptions per user
   - Efficient filtering to avoid notification storms

**Implementation Effort:** 2-3 weeks (1 developer)

**Priority:** 🟡 **MEDIUM** - Optional feature but valuable for integration

**Testing Requirements:**
- Test subscription CRUD operations
- Test notification delivery
- Test filter matching logic
- Test retry logic
- Test async delivery (non-blocking)

---

### 4.3 Future Enhancement Gaps (Deferred)

These gaps are not required for minimum viable MEO but enhance functionality for advanced scenarios.

#### **Gap 9: Advanced Placement Algorithm**

**Requirement:** ETSI GS MEC 003 §6.2.1.3 (advanced capabilities)

**Current Gap:** Only basic resource-based placement

**Future Implementation:**
- Multi-criteria optimization (weighted scoring)
- Latency-aware placement (require topology map)
- Service-aware placement (require service catalog)
- Affinity/anti-affinity rules
- Cost optimization
- Load balancing across hosts
- Historical analysis and learning

**Priority:** 🟢 **LOW** - Basic placement sufficient for initial deployments

---

#### **Gap 10: Application Relocation (Stateful)**

**Requirement:** ETSI GS MEC 003 §6.2.1.4

**Current Gap:** Only manual stateless relocation

**Future Implementation:**
- Automatic relocation triggers (monitoring + policy)
- Stateful migration with state transfer
- Traffic management during migration
- Zero-downtime migration (live migration)
- Rollback on migration failure

**Priority:** 🟢 **LOW** - Stateless relocation via redeploy is acceptable initially

---

#### **Gap 11: Multi-MEPM Coordination**

**Current Gap:** Basic Mm5 client supports multi-MEPM, but no coordination

**Future Implementation:**
- MEPM registry and discovery
- Advanced MEPM selection (not just first-fit)
- Load balancing across MEPMs
- Quota management per MEPM
- Cross-MEPM operations

**Priority:** 🟢 **LOW** - Single MEPM sufficient for initial deployments

---

#### **Gap 12: Network Topology & Service Discovery**

**Requirement:** ETSI GS MEC 003 §6.2.1.1

**Current Gap:** Device inventory exists, but no topology mapping

**Future Implementation:**
- Network topology map (connectivity, latency matrix)
- Service catalog (what MEC services each host provides)
- Service discovery protocol
- Dynamic topology updates
- Latency measurement infrastructure

**Priority:** 🟢 **LOW** - Not needed for basic placement

---

## 5. Implementation Roadmap

This section provides a phased approach to achieving MEO compliance.

### 5.1 Phase 1: Minimum Viable MEO (6-8 weeks)

**Goal:** Demonstrate core MEO functionality with standards compliance

**Scope:**
- 9 required MEC 010-2 API endpoints
- Mm5 interface (MEO-MEPM communication)
- Basic resource-based placement
- Operation occurrence tracking

**Deliverables:**
1. **MEC 010-2 API Implementation** (4-6 weeks)
   - 9 REST endpoints with full CRUD
   - Data models and validation
   - State management (instantiation, operational)
   - Query filtering and pagination
   - HATEOAS navigation
   - Unit tests (100+ tests)
   - Integration tests (end-to-end flows)
   - OpenAPI 3.0 specification

2. **Mm5 Client Library** (2-3 weeks)
   - HTTP client with 5+ operations
   - Retry logic and error handling
   - MEPM health monitoring
   - Mock MEPM for testing
   - Integration with lifecycle handler

3. **Basic Placement Algorithm** (1-2 weeks)
   - First-fit resource-based selection
   - MEPM capability matching
   - Resource availability checking
   - Placement validation

4. **Operation Tracking** (2-3 weeks)
   - AppLcmOpOcc implementation
   - Operation recording wrapper
   - Query API with filters
   - Persistence integration

**Success Criteria:**
- ✅ 9 MEC 010-2 endpoints operational
- ✅ 100+ tests passing
- ✅ Basic app deployment works end-to-end
- ✅ Operation history queryable
- ✅ Mm5 communication with MEPM works

**Risk:** Medium - Significant new code, but well-defined requirements

---

### 5.2 Phase 2: Production MEO (4-6 weeks)

**Goal:** Add production-essential features for operator control and integration

**Scope:**
- Package integrity checking (DCT)
- Policy validation (OPA)
- RFC 7807 error handling
- Subscription & notification system

**Deliverables:**
1. **Docker Content Trust** (3-4 weeks)
   - DCT integration for Docker images
   - Notary service deployment
   - K8s manifest signing (cosign)
   - Trust policy configuration
   - Testing with signed/unsigned images

2. **OPA Policy Engine** (3-4 weeks)
   - OPA deployment
   - Policy definitions (resource, security, network, compliance)
   - Integration with deployment workflow
   - Policy testing framework
   - Documentation

3. **RFC 7807 Error Handling** (1-2 weeks)
   - ProblemDetails implementation
   - 13 error types with URIs
   - MEC-specific extensions
   - Error documentation

4. **Subscription System** (2-3 weeks)
   - 4 subscription endpoints
   - 2 notification types
   - Webhook delivery with retries
   - Filter matching
   - Async delivery

**Success Criteria:**
- ✅ Unsigned images rejected (DCT)
- ✅ Policy violations blocked (OPA)
- ✅ All errors in RFC 7807 format
- ✅ Notifications delivered on events
- ✅ Integration tests passing

**Risk:** Medium - OPA and DCT require infrastructure, but feasibility proven

---

### 5.3 Phase 3: Advanced MEO (Future, 6-12 months)

**Goal:** Advanced features for sophisticated multi-host scenarios

**Scope:**
- Advanced placement algorithms
- Stateful application relocation
- Multi-MEPM coordination
- Network topology awareness
- Fault management

**Deliverables:**
- Multi-criteria placement optimization
- Automatic relocation with triggers
- State transfer for stateful apps
- Traffic management during migration
- Topology mapping and service discovery
- MEPM load balancing

**Success Criteria:**
- ✅ Latency-aware placement
- ✅ Zero-downtime migration
- ✅ Multi-MEPM load balancing
- ✅ Automatic failure recovery

**Risk:** High - Complex distributed systems challenges

**Priority:** Deferred - Not required for initial MEO registration

---

## 6. Minimum Viable MEO Definition

For MECwiki registration and initial production deployment, the minimum viable MEO must have:

### 6.1 Core Capabilities (Must Have)

**✅ MEC 010-2 API Compliance**
- 9 required REST endpoints operational
- AppInstanceInfo and AppLcmOpOcc data models
- State management (instantiation + operational)
- Query, filtering, pagination
- HATEOAS navigation
- 80%+ compliance with MEC 010-2

**✅ Mm5 Interface (MEO-MEPM)**
- HTTP client for MEPM communication
- 5 core operations (health, capabilities, resources, deploy, status, terminate)
- Retry logic and error handling
- Support for multiple MEPM backends

**✅ Application Lifecycle Operations**
- Create app instance
- Instantiate (deploy) to MEPM
- Terminate (undeploy) from MEPM
- Operate (start/stop)
- Query instances and operations

**✅ Basic Host Selection**
- Resource-based placement (first-fit)
- MEPM capability matching
- Resource availability checking

**✅ Operation Tracking**
- Record all lifecycle operations
- Queryable history with filtering
- State tracking (starting → completed/failed)

### 6.2 Important Capabilities (Should Have)

**⚠️ Error Handling**
- RFC 7807 ProblemDetails format preferred
- At minimum: consistent error responses with codes

**⚠️ Documentation**
- OpenAPI 3.0 specification
- Integration guide for MEPM developers
- Deployment documentation

**⚠️ Testing**
- Comprehensive unit test coverage (80%+)
- Integration tests for end-to-end flows
- Mock MEPM for deterministic testing

### 6.3 Future Capabilities (Nice to Have)

**📋 Deferred to Post-Registration:**
- Package integrity checking (DCT)
- Policy validation (OPA)
- Subscription & notification system
- Advanced placement algorithms
- Stateful application relocation
- Multi-MEPM coordination

---

## 7. MECwiki Registration Criteria

Based on this gap analysis, the following criteria must be met for MECwiki registration as a MEC Orchestrator:

### 7.1 Functional Criteria

| Criterion | Requirement | Phase |
|-----------|-------------|-------|
| **MEC 010-2 API** | 9 endpoints, 80%+ compliant | Phase 1 |
| **Mm5 Interface** | 5 operations, working with MEPM | Phase 1 |
| **Lifecycle Management** | Instantiate, terminate, operate | Phase 1 |
| **Host Selection** | Basic resource-based placement | Phase 1 |
| **Operation Tracking** | History with queries | Phase 1 |

### 7.2 Quality Criteria

| Criterion | Requirement |
|-----------|-------------|
| **Test Coverage** | 80%+ code coverage, 100+ tests |
| **Documentation** | OpenAPI spec + integration guide |
| **Error Handling** | Consistent error responses |
| **Production Deployment** | At least one successful production deployment |

### 7.3 Documentation Criteria

| Document | Required Content |
|----------|-----------------|
| **Architecture Documentation** | MEO role, component mapping, reference points |
| **API Specification** | OpenAPI 3.0 with all endpoints |
| **Integration Guide** | MEPM integration instructions |
| **Compliance Matrix** | Gap analysis with MEC 003/010-2 mapping |
| **Deployment Guide** | Installation and configuration instructions |

### 7.4 Timeline for Registration

**Minimum Time to Registration:** 10-14 weeks
- Phase 1 (Minimum Viable MEO): 6-8 weeks
- Testing & documentation: 2-3 weeks
- Production validation: 2-3 weeks

**Recommended Timeline:** 14-18 weeks (include Phase 2 for production readiness)
- Phase 1: 6-8 weeks
- Phase 2: 4-6 weeks
- Testing & documentation: 2-3 weeks
- Production validation: 2-3 weeks

---

## 8. Resource Requirements

### 8.1 Development Resources

**Phase 1 (Minimum Viable MEO):**
- 1-2 senior developers (full-time)
- 0.5 QA engineer (testing)
- 0.2 technical writer (documentation)
- 0.1 DevOps engineer (infrastructure)

**Estimated Effort:** 6-8 weeks × 1.8 FTE = 11-14 person-weeks

**Phase 2 (Production MEO):**
- 1-2 senior developers (full-time)
- 0.5 QA engineer (testing)
- 0.2 technical writer (documentation)
- 0.3 DevOps engineer (DCT/OPA infrastructure)

**Estimated Effort:** 4-6 weeks × 2 FTE = 8-12 person-weeks

**Total Phase 1 + 2:** 19-26 person-weeks

### 8.2 Infrastructure Requirements

**Development/Testing:**
- Development environment (Nuvla instance)
- Mock MEPM server (created as part of development)
- Test edge devices or VMs (3-5 instances)
- CI/CD pipeline integration

**Production:**
- Notary service for DCT (if Phase 2)
- OPA server (if Phase 2)
- Monitoring and logging infrastructure
- Database for operation tracking

### 8.3 Budget Estimate

**Development Costs (Phase 1 + 2):**
- Development: 19-26 weeks × €5,000/week = €95,000 - €130,000
- QA: 5 weeks × €4,000/week = €20,000
- Documentation: 2 weeks × €4,000/week = €8,000
- DevOps: 2 weeks × €5,000/week = €10,000

**Total Development:** €133,000 - €168,000

**Infrastructure Costs:**
- Development: €2,000 (one-time)
- Testing: €1,000/month × 3 months = €3,000
- Production: €3,000 (one-time) + €1,000/month ongoing

**Total Phase 1 + 2:** €138,000 - €173,000 (one-time) + €1,000/month (ongoing)

---

## 9. Risk Assessment

### 9.1 Technical Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| **Integration complexity** | High | Medium | Start with mock MEPM, incremental integration |
| **State management complexity** | Medium | Medium | Leverage existing Nuvla job system |
| **Performance issues** | Medium | Low | Load testing early, optimize queries |
| **MEPM availability** | High | Low | Retry logic, failover to backup MEPM |
| **DCT infrastructure setup** | Medium | Medium | Use managed Notary service or defer to Phase 2 |
| **OPA policy complexity** | Medium | Medium | Start with simple policies, iterate |

### 9.2 Schedule Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| **Underestimated complexity** | High | Medium | Add 20% buffer to estimates, phased approach |
| **Resource availability** | High | Low | Secure dedicated team commitment upfront |
| **Scope creep** | Medium | High | Strict phase definitions, deferred feature list |
| **Testing delays** | Medium | Medium | Automated testing, early test development |

### 9.3 Business Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| **Standards evolution** | Low | Medium | Monitor ETSI updates, modular design |
| **Market adoption** | Medium | Low | Focus on 5G-EMERGE use case first |
| **Competition** | Low | Low | Open source advantage, Nuvla ecosystem |

---

## 10. Success Metrics

### 10.1 Technical Metrics

**API Compliance:**
- ✅ Target: 80%+ MEC 010-2 compliance (Phase 1)
- ✅ Target: 90%+ MEC 010-2 compliance (Phase 2)

**Code Quality:**
- ✅ Target: 80%+ test coverage
- ✅ Target: 100+ unit tests
- ✅ Target: 10+ integration tests
- ✅ Target: 0 critical bugs in production

**Performance:**
- ✅ Target: <500ms API response time (P95)
- ✅ Target: 100+ concurrent operations
- ✅ Target: 1000+ app instances manageable

**Reliability:**
- ✅ Target: 99.9% API uptime
- ✅ Target: <1% failed deployments (excluding user errors)
- ✅ Target: 100% operation tracking (no lost operations)

### 10.2 Business Metrics

**MECwiki Registration:**
- ✅ Target: Listed as MEO in MECwiki
- ✅ Target: Compliance matrix published
- ✅ Target: Reference architecture documented

**Adoption:**
- ✅ Target: 1+ production deployment (5G-EMERGE)
- ✅ Target: 10+ apps managed
- ✅ Target: 5+ edge hosts orchestrated

**Community:**
- ✅ Target: Documentation published
- ✅ Target: Integration guide available
- ✅ Target: Example MEPM implementation reference

---

## 11. Conclusion

This comprehensive gap analysis provides a clear roadmap for achieving MEC Orchestrator (MEO) compliance for the Nuvla platform. The phased approach balances minimum viable functionality (Phase 1) with production readiness (Phase 2) and future enhancements (Phase 3).

### 11.1 Summary of Gaps

**Critical Gaps (Phase 1 - 6-8 weeks):**
1. ✅ MEC 010-2 API implementation (9 endpoints)
2. ✅ Mm5 interface (MEO-MEPM communication)
3. ✅ Basic placement algorithm (resource-based)
4. ✅ Operation occurrence tracking

**Important Gaps (Phase 2 - 4-6 weeks):**
5. ⚠️ Package integrity checking (DCT)
6. ⚠️ Policy validation (OPA)
7. ⚠️ RFC 7807 error handling
8. ⚠️ Subscription & notification system

**Future Gaps (Phase 3 - Deferred):**
9. 📋 Advanced placement algorithms
10. 📋 Stateful application relocation
11. 📋 Multi-MEPM coordination
12. 📋 Network topology awareness

### 11.2 Recommended Next Steps

1. **Secure Resources** (Week 1)
   - Allocate 1-2 senior developers
   - Assign QA and documentation support
   - Reserve infrastructure budget

2. **Phase 1 Kickoff** (Week 1-2)
   - Architecture design workshop
   - Component interface definitions
   - Development environment setup
   - Sprint planning

3. **Phase 1 Implementation** (Week 2-8)
   - MEC 010-2 API development
   - Mm5 client implementation
   - Placement algorithm
   - Operation tracking
   - Testing and documentation

4. **Phase 1 Validation** (Week 9-10)
   - Integration testing
   - Mock MEPM validation
   - Documentation review
   - Demo preparation

5. **Phase 2 Planning** (Week 10)
   - Review Phase 1 results
   - Approve Phase 2 scope
   - Infrastructure requirements

6. **Phase 2 Implementation** (Week 11-16)
   - DCT integration
   - OPA integration
   - Error handling
   - Subscription system

7. **Registration Preparation** (Week 17-18)
   - Compliance matrix finalization
   - MECwiki submission preparation
   - Production deployment validation

8. **MECwiki Registration** (Week 18+)
   - Submit to MECwiki
   - Publish documentation
   - Community engagement

### 11.3 Expected Outcomes

**By Phase 1 Completion (Week 10):**
- ✅ Minimum viable MEO functional
- ✅ 9 MEC 010-2 endpoints operational
- ✅ Mm5 communication with MEPM working
- ✅ Basic app deployment end-to-end
- ✅ Demonstrable to stakeholders

**By Phase 2 Completion (Week 18):**
- ✅ Production-ready MEO
- ✅ Security features operational (DCT)
- ✅ Operator control enabled (OPA)
- ✅ Standards-compliant error handling
- ✅ Integration capabilities (subscriptions)
- ✅ Ready for MECwiki registration

**Long-term (6-12 months):**
- 📋 Advanced MEO capabilities
- 📋 Multi-host orchestration at scale
- 📋 Industry recognition as MEO solution
- 📋 5G-EMERGE project success

---

## Appendix A: Nuvla Existing Capabilities

This appendix documents Nuvla's existing capabilities that align with MEO requirements, to avoid duplicating functionality.

### A.1 Application Package Management

**Existing (Module Resources):**
- ✅ Store application definitions (Docker images, K8s manifests)
- ✅ Version management (multiple versions per app)
- ✅ Application metadata (name, author, description, license)
- ✅ Access control (public/private modules)
- ✅ Application catalog/registry
- ✅ Multi-architecture support (x86_64, arm64)

**Gaps:**
- ⚠️ Package integrity checking (no DCT)
- ⚠️ Policy validation (no OPA)

**Recommendation:** Extend existing module system with DCT and OPA, don't rebuild from scratch.

### A.2 Edge Infrastructure Management

**Existing (NuvlaBox Resources):**
- ✅ Edge device registration and inventory
- ✅ Resource monitoring (CPU, memory, storage)
- ✅ Health monitoring and status updates
- ✅ Peripheral device tracking
- ✅ Multi-device management

**Gaps:**
- ⚠️ Network topology mapping (no latency matrix)
- ⚠️ Service catalog (no MEC service discovery)

**Recommendation:** Add topology and service catalog on top of existing NuvlaBox inventory.

### A.3 Lifecycle Management

**Existing (Deployment Resources):**
- ✅ Application instantiation (deployment)
- ✅ Application termination (undeployment)
- ✅ State management (created, starting, started, stopped, error)
- ✅ Multi-cloud orchestration

**Gaps:**
- ⚠️ Not MEC 010-2 compliant (different API, data models, states)
- ⚠️ No Mm5 interface (no explicit MEPM delegation)

**Recommendation:** Create new MEC 010-2 API layer that delegates to existing deployment system. Map Nuvla deployment states to MEC states.

### A.4 Event System

**Existing (Event Resources):**
- ✅ Event publication on resource changes
- ✅ Kafka integration for event streaming
- ✅ Event filtering and routing

**Gaps:**
- ⚠️ Not MEC 010-2 compliant (different notification format)
- ⚠️ No webhook delivery with retries

**Recommendation:** Add MEC 010-2 notification layer on top of existing event system.

### A.5 Job System

**Existing (Job Resources):**
- ✅ Asynchronous operation tracking
- ✅ Job state management (queued, running, success, failed)
- ✅ Job history and auditing
- ✅ Persistence and querying

**Gaps:**
- ⚠️ Not MEC 010-2 AppLcmOpOcc format

**Recommendation:** Map job resources to AppLcmOpOcc, leverage existing persistence and query capabilities.

### A.6 Multi-tenancy & Access Control

**Existing (ACL System):**
- ✅ Multi-tenancy support (users, groups)
- ✅ Role-based access control (RBAC)
- ✅ Resource ownership and sharing
- ✅ Fine-grained permissions

**Gaps:**
- ✅ None - existing system is sufficient

**Recommendation:** Use existing ACL system for MEC 010-2 API authorization.

---

## Appendix B: Reference Standards

**Primary Standards:**
- [MEC 003] ETSI GS MEC 003 v3.1.1 - Multi-access Edge Computing (MEC); Framework and Reference Architecture
- [MEC 010-2] ETSI GS MEC 010-2 v2.2.1 - Multi-access Edge Computing (MEC); MEC Management; Part 2: Application lifecycle, rules and requirements management
- [MEC 002] ETSI GS MEC 002 v2.2.1 - Multi-access Edge Computing (MEC); Technical Requirements

**Supporting Standards:**
- [RFC 7807] Problem Details for HTTP APIs
- [RFC 7231] Hypertext Transfer Protocol (HTTP/1.1): Semantics and Content
- [OpenAPI 3.0.3] OpenAPI Specification v3.0.3

**Related MEC Standards:**
- [MEC 001] ETSI GS MEC 001 v3.1.1 - Terminology
- [MEC 009] ETSI GS MEC 009 v3.1.1 - General principles for MEC Service APIs
- [MEC 011] ETSI GS MEC 011 v2.2.1 - Edge Platform Application Enablement

---

**Document Version:** 2.0  
**Status:** Final  
**Next Review:** Upon Phase 1 completion  
**Owner:** Nuvla Engineering Team / 5G-EMERGE Project
