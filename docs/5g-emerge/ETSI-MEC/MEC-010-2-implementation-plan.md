# MEC 010-2 Implementation Plan
## Application Lifecycle Management API Compliance

**Document Version:** 1.0  
**Date:** 21 October 2025  
**Project:** 5G-EMERGE / Nuvla.io  
**Target Standard:** ETSI GS MEC 010-2 v2.2.1

---

## Executive Summary

This document outlines a detailed implementation plan to achieve **MEC 010-2 compliance** for the Nuvla.io API server. MEC 010-2 defines the Application Lifecycle Management (LCM) APIs that enable mobile edge applications to be instantiated, configured, and terminated on MEC platforms.

**Current Status:** ~55% compliant  
**Target:** 90%+ compliant  
**Estimated Effort:** 14 weeks (560 hours)  
**Team Size:** 3-4 developers + 0.5 FTE QA + 0.2 FTE PM  
**Investment:** €140k - €175k

---

## 1. Current State Assessment

### 1.1 Existing Strengths (What We Have)

Nuvla already has strong lifecycle management capabilities:

| Component | Status | Coverage |
|-----------|--------|----------|
| **Deployment Resource** | ✅ Mature | Start, stop, update operations |
| **Module Resource** | ✅ Mature | Application definitions, versions |
| **NuvlaBox Resource** | ✅ Mature | Edge infrastructure management |
| **Credential Resource** | ✅ Mature | Authentication & secrets |
| **Job Resource** | ✅ Mature | Asynchronous operations |
| **Event Resource** | ✅ Mature | Lifecycle events & notifications |
| **Infrastructure Service** | ✅ Mature | Multi-cloud orchestration |

**Key Capabilities:**
- ✅ Application instantiation via deployment resources
- ✅ Lifecycle state management (created, starting, started, stopped, error)
- ✅ Docker/Kubernetes orchestration
- ✅ Multi-tenancy and RBAC
- ✅ Event-driven architecture with Kafka
- ✅ RESTful API design

### 1.2 Compliance Gaps

| MEC 010-2 Requirement | Current Status | Gap |
|----------------------|----------------|-----|
| **AppInstanceInfo** | Partial | Missing MEC-specific fields |
| **AppLcmOpOcc** | None | No operation occurrence tracking |
| **AppInstanceSubscription** | Partial | Limited notification types |
| **Standardized State Model** | Partial | States don't match MEC exactly |
| **Error Handling** | Good | Needs MEC ProblemDetails format |
| **Query Filters** | Basic | Need attribute-based selectors |
| **VNFM-like Interface** | None | Missing ETSI NFV alignment |

---

## 2. Implementation Phases

### Phase 1: Core API Alignment (Weeks 1-5)
**Goal:** Map existing resources to MEC 010-2 data models

#### 2.1 AppInstanceInfo Data Model (Week 1-2)

**Tasks:**
1. Create MEC 010-2 schema definitions
   - Define AppInstanceInfo schema in `resources/mec-010-2-schemas.json`
   - Add JSON Schema validators
   - Document field mappings from deployment → AppInstanceInfo

2. Extend deployment resource
   ```clojure
   ;; Add to sixsq.nuvla.server.resources.deployment
   (def mec-010-2-attrs
     {:appInstanceId           {:type "string"}
      :appDId                  {:type "string"}  ;; Links to module
      :appProvider             {:type "string"}
      :appName                 {:type "string"}
      :appSoftVersion          {:type "string"}
      :appDVersion             {:type "string"}
      :mecHostInformation      {:type "map"}     ;; NuvlaBox details
      :instantiationState      {:type "string"
                                :enum ["NOT_INSTANTIATED" "INSTANTIATED"]}
      :operationalState        {:type "string"
                                :enum ["STARTED" "STOPPED"]}
      :_links                  {:type "map"}})   ;; HATEOAS links
   ```

3. Create state mapping layer
   - Map Nuvla states → MEC 010-2 states
   - Handle state transitions
   - Add validation for state changes

**Deliverables:**
- ✅ MEC 010-2 schema files
- ✅ Extended deployment resource model
- ✅ State mapping functions
- ✅ Unit tests for data transformations

**Effort:** 80 hours (2 devs × 1 week)

---

#### 2.2 API Endpoint Restructuring (Week 3-4)

**Tasks:**
1. Create new MEC API namespace
   ```
   src/com/sixsq/nuvla/server/resources/mec/
   ├── app_instances.clj          ;; Main AppInstance API
   ├── app_lcm_op_occs.clj        ;; Operation occurrences
   ├── subscriptions.clj          ;; Notifications
   └── common.clj                 ;; Shared utilities
   ```

2. Implement MEC 010-2 endpoints
   ```
   POST   /mec/app_lcm/v2/app_instances
   GET    /mec/app_lcm/v2/app_instances
   GET    /mec/app_lcm/v2/app_instances/{appInstanceId}
   DELETE /mec/app_lcm/v2/app_instances/{appInstanceId}
   POST   /mec/app_lcm/v2/app_instances/{appInstanceId}/instantiate
   POST   /mec/app_lcm/v2/app_instances/{appInstanceId}/terminate
   POST   /mec/app_lcm/v2/app_instances/{appInstanceId}/operate
   ```

3. Create API adapters
   - Translate MEC requests → Nuvla operations
   - Convert Nuvla responses → MEC format
   - Maintain backward compatibility with existing API

4. Add HATEOAS links
   - Self links for all resources
   - Related resource links (appDId, subscriptions)
   - Operation links based on current state

**Deliverables:**
- ✅ New MEC API namespace
- ✅ 7+ new REST endpoints
- ✅ Request/response adapters
- ✅ HATEOAS link generation
- ✅ Integration tests

**Effort:** 120 hours (3 devs × 1.3 weeks)

---

#### 2.3 Operation Occurrence Tracking (Week 5)

**Tasks:**
1. Create AppLcmOpOcc resource
   ```clojure
   ;; New resource: sixsq.nuvla.server.resources.app-lcm-op-occ
   (def resource-type "app-lcm-op-occ")
   
   (def ^:const resource-attrs
     {:id                      {:type "string"}
      :operationState          {:type "string"
                                :enum ["STARTING" "PROCESSING" "COMPLETED" 
                                       "FAILED_TEMP" "FAILED" "ROLLING_BACK" 
                                       "ROLLED_BACK"]}
      :lcmOperation            {:type "string"
                                :enum ["INSTANTIATE" "TERMINATE" "OPERATE"]}
      :appInstanceId           {:type "string"}
      :startTime               {:type "timestamp"}
      :stateEnteredTime        {:type "timestamp"}
      :error                   {:type "map"}})   ;; ProblemDetails
   ```

2. Link to existing job resource
   - Create operation occurrence when job starts
   - Update state as job progresses
   - Record errors in MEC format

3. Implement query endpoints
   ```
   GET /mec/app_lcm/v2/app_lcm_op_occs
   GET /mec/app_lcm/v2/app_lcm_op_occs/{appLcmOpOccId}
   ```

**Deliverables:**
- ✅ AppLcmOpOcc resource definition
- ✅ Job integration layer
- ✅ Query endpoints
- ✅ State tracking logic

**Effort:** 60 hours (2 devs × 0.75 weeks)

---

### Phase 2: Enhanced Functionality (Weeks 6-9)
**Goal:** Add MEC-specific features and notifications

#### 2.4 Subscription & Notification System (Week 6-7)

**Tasks:**
1. Create AppInstanceSubscription resource
   ```clojure
   (def subscription-attrs
     {:id                      {:type "string"}
      :subscriptionType        {:type "string"
                                :enum ["AppInstanceStateChangeNotification"
                                       "AppLcmOpOccStateChangeNotification"]}
      :callbackUri             {:type "uri"}
      :appInstanceFilter       {:type "map"}    ;; Filter criteria
      :_links                  {:type "map"}})
   ```

2. Implement subscription endpoints
   ```
   POST   /mec/app_lcm/v2/subscriptions
   GET    /mec/app_lcm/v2/subscriptions
   GET    /mec/app_lcm/v2/subscriptions/{subscriptionId}
   DELETE /mec/app_lcm/v2/subscriptions/{subscriptionId}
   ```

3. Build notification dispatcher
   - Listen to Kafka events (already in place)
   - Match events against subscriptions
   - Format notifications per MEC spec
   - HTTP POST to callback URIs
   - Handle retries and failures

4. Add notification types
   ```json
   {
     "notificationType": "AppInstanceStateChangeNotification",
     "appInstanceId": "deployment/abc-123",
     "operationalState": "STARTED",
     "changeType": "OPERATIONAL_STATE",
     "_links": {...}
   }
   ```

**Deliverables:**
- ✅ Subscription resource and API
- ✅ Notification dispatcher service
- ✅ Event-to-notification mapping
- ✅ Webhook delivery system
- ✅ Integration tests with mock subscribers

**Effort:** 120 hours (3 devs × 1.3 weeks)

---

#### 2.5 Query Filters & Pagination (Week 8)

**Tasks:**
1. Implement attribute-based filtering
   ```
   GET /mec/app_lcm/v2/app_instances?filter=(eq,appName,my-app)
   GET /mec/app_lcm/v2/app_instances?filter=(eq,operationalState,STARTED)
   ```

2. Add MEC-compliant pagination
   ```json
   {
     "_links": {
       "self": {"href": "/app_instances?page=1&size=20"},
       "next": {"href": "/app_instances?page=2&size=20"}
     }
   }
   ```

3. Implement field selectors
   ```
   GET /mec/app_lcm/v2/app_instances?fields=appName,operationalState
   ```

**Deliverables:**
- ✅ Filter parser (FIQL-like)
- ✅ Pagination support
- ✅ Field selection
- ✅ Query optimization

**Effort:** 60 hours (2 devs × 0.75 weeks)

---

#### 2.6 Error Handling & ProblemDetails (Week 9)

**Tasks:**
1. Implement RFC 7807 ProblemDetails format
   ```clojure
   (defn problem-details
     [type title status detail instance]
     {:type     type      ;; URI reference
      :title    title     ;; Human-readable summary
      :status   status    ;; HTTP status code
      :detail   detail    ;; Human-readable explanation
      :instance instance  ;; URI reference to specific occurrence
      })
   ```

2. Create error type taxonomy
   ```
   /errors/insufficient-resources
   /errors/invalid-state-transition
   /errors/resource-not-found
   /errors/authentication-failure
   ```

3. Update all error responses
   - Convert existing error responses to ProblemDetails
   - Add proper HTTP status codes
   - Include actionable error details

**Deliverables:**
- ✅ ProblemDetails middleware
- ✅ Error type definitions
- ✅ Updated error responses across all endpoints

**Effort:** 40 hours (1 dev × 1 week)

---

### Phase 3: Testing & Documentation (Weeks 10-14)
**Goal:** Ensure compliance and production readiness

#### 2.7 Comprehensive Testing (Week 10-12)

**Tasks:**
1. Unit tests (Week 10)
   - Data model transformations
   - State transitions
   - Filter parsing
   - Error handling

2. Integration tests (Week 11)
   - Full lifecycle workflows
   - Multi-instance scenarios
   - Subscription & notification flows
   - Error recovery scenarios

3. Compliance testing (Week 12)
   - Create MEC 010-2 test suite
   - Validate against official conformance tests (if available)
   - Test with MEC emulators
   - Performance testing (100+ concurrent instances)

4. Security testing
   - Authentication & authorization
   - Input validation
   - Rate limiting
   - Injection attacks

**Deliverables:**
- ✅ 100+ unit tests
- ✅ 50+ integration tests
- ✅ Compliance test suite
- ✅ Performance benchmarks
- ✅ Security audit report

**Effort:** 160 hours (2 devs + 1 QA × 2 weeks)

---

#### 2.8 Documentation (Week 13-14)

**Tasks:**
1. API documentation
   - OpenAPI 3.0 specification
   - Interactive API explorer (Swagger UI)
   - Request/response examples
   - Error codes reference

2. Integration guides
   - Quick start guide
   - Migration from legacy API
   - Best practices
   - Code examples (Python, JavaScript, curl)

3. Operational documentation
   - Deployment configuration
   - Monitoring & observability
   - Troubleshooting guide
   - Performance tuning

4. Compliance documentation
   - MEC 010-2 coverage matrix
   - Deviation explanations
   - Conformance test results

**Deliverables:**
- ✅ OpenAPI spec (mec-010-2-api.yaml)
- ✅ Developer documentation
- ✅ Operations runbook
- ✅ Compliance report

**Effort:** 80 hours (1 dev + 1 tech writer × 2 weeks)

---

## 3. Technical Architecture

### 3.1 Component Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    MEC 010-2 API Layer                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ App Instance │  │   AppLcmOp   │  │ Subscription │     │
│  │     API      │  │    Occ API   │  │     API      │     │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘     │
│         │                  │                  │             │
└─────────┼──────────────────┼──────────────────┼─────────────┘
          │                  │                  │
┌─────────▼──────────────────▼──────────────────▼─────────────┐
│              Adapter & Translation Layer                     │
│  ┌────────────────────────────────────────────────────┐     │
│  │  • MEC ↔ Nuvla data model mapping                 │     │
│  │  • State transition validation                     │     │
│  │  • HATEOAS link generation                        │     │
│  │  • ProblemDetails error formatting                │     │
│  └────────────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────────────┘
          │                  │                  │
┌─────────▼──────────────────▼──────────────────▼─────────────┐
│              Existing Nuvla Core Resources                   │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │Deployment│  │   Job    │  │  Event   │  │   ACL    │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└──────────────────────────────────────────────────────────────┘
          │                  │                  │
┌─────────▼──────────────────▼──────────────────▼─────────────┐
│                    Infrastructure Layer                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │   K8s    │  │  Docker  │  │  Kafka   │  │   ES     │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 Data Flow Example: Instantiate Application

```
1. Client → POST /mec/app_lcm/v2/app_instances/{id}/instantiate
             {
               "additionalParams": {...}
             }

2. MEC API Layer
   ├─ Validate request against MEC schema
   ├─ Check authentication & authorization
   └─ Create AppLcmOpOcc (operationState: STARTING)

3. Adapter Layer
   ├─ Map to Nuvla deployment action
   ├─ Translate MEC params → Nuvla config
   └─ Validate state (must be NOT_INSTANTIATED)

4. Nuvla Core
   ├─ Create job resource
   ├─ Update deployment state → starting
   └─ Emit event to Kafka

5. Infrastructure
   ├─ K8s/Docker creates containers
   └─ Reports status back

6. Event Processing
   ├─ Kafka event → Update AppLcmOpOcc (COMPLETED)
   ├─ Update AppInstanceInfo (instantiationState: INSTANTIATED)
   └─ Trigger subscriptions

7. Notification Dispatcher
   ├─ Match event against subscriptions
   ├─ Format AppInstanceStateChangeNotification
   └─ POST to callback URIs

8. Client ← 202 Accepted
             {
               "appLcmOpOccId": "app-lcm-op-occ/xyz-789"
             }
```

---

## 4. Resource Estimates

### 4.1 Effort Breakdown

| Phase | Tasks | Dev Hours | QA Hours | Total |
|-------|-------|-----------|----------|-------|
| **Phase 1** | Core API Alignment | 200 | 60 | 260 |
| **Phase 2** | Enhanced Features | 180 | 40 | 220 |
| **Phase 3** | Testing & Docs | 80 | 160 | 240 |
| **PM/Overhead** | Planning, reviews, meetings | - | - | 80 |
| **TOTAL** | | 460 | 260 | **800** |

### 4.2 Team Structure

**Core Team:**
- 2x Senior Backend Developers (Clojure expertise)
- 1x Backend Developer (API design)
- 1x QA Engineer (0.5 FTE)
- 1x Project Manager (0.2 FTE)

**Specialist Support (as needed):**
- 0.5 FTE Solutions Architect (architecture review, weeks 1-2 & 13-14)
- 0.2 FTE Technical Writer (documentation, weeks 13-14)

### 4.3 Investment Estimate

**Personnel Costs:**
- Senior Developer: €800/day × 2 × 70 days = €112,000
- Developer: €650/day × 1 × 70 days = €45,500
- QA Engineer: €600/day × 0.5 × 70 days = €21,000
- PM: €700/day × 0.2 × 70 days = €9,800
- Architect: €900/day × 0.5 × 20 days = €9,000
- Tech Writer: €500/day × 0.2 × 10 days = €1,000

**Total Personnel:** €198,300

**Conservative Estimate (with 20% buffer):** €140k - €175k

---

## 5. Risk Assessment

### 5.1 Technical Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| **State model incompatibility** | Medium | High | Early prototyping of state mappings |
| **Performance degradation** | Low | Medium | Load testing, caching strategies |
| **Breaking changes to existing API** | Low | High | Maintain v1 API, versioned endpoints |
| **Notification delivery failures** | Medium | Medium | Retry mechanism, dead letter queue |
| **Complex filter queries** | Medium | Low | Start with simple filters, iterate |

### 5.2 Organizational Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| **Resource availability** | Medium | High | Secure team commitment upfront |
| **Scope creep** | Medium | Medium | Clear requirements, change control |
| **Integration with 5G-EMERGE** | Low | Medium | Regular sync with project team |
| **MEC spec changes** | Low | Medium | Monitor ETSI releases, modular design |

---

## 6. Success Criteria

### 6.1 Functional Requirements

- ✅ All MEC 010-2 mandatory APIs implemented
- ✅ AppInstance lifecycle operations (instantiate, terminate, operate)
- ✅ Operation occurrence tracking with state management
- ✅ Subscription & notification system
- ✅ Query filters and pagination
- ✅ ProblemDetails error handling

### 6.2 Non-Functional Requirements

- ✅ **Performance:** Support 100+ concurrent app instances
- ✅ **Availability:** 99.9% uptime
- ✅ **Response Time:** <200ms for GET, <500ms for POST/DELETE
- ✅ **Compliance:** 90%+ coverage of MEC 010-2 requirements
- ✅ **Backward Compatibility:** Existing API remains functional

### 6.3 Quality Gates

**Week 5 Checkpoint:**
- Core API endpoints operational
- Basic data model mapping complete
- Integration tests passing

**Week 9 Checkpoint:**
- Subscription system working
- Notifications being delivered
- Query filters implemented

**Week 14 Final:**
- All tests passing (>90% coverage)
- Documentation complete
- Compliance validation report
- Production deployment ready

---

## 7. Dependencies & Prerequisites

### 7.1 Technical Dependencies

- ✅ Elasticsearch 7.x+ (already in place)
- ✅ Apache Kafka (already in place)
- ✅ Clojure 1.12.0 (already in place)
- ✅ Ring/Compojure (already in place)
- ⚠️ OpenAPI tooling (need to add)
- ⚠️ MEC emulator/sandbox (for testing)

### 7.2 Knowledge Requirements

- Understanding of ETSI MEC architecture
- Experience with RESTful API design
- Familiarity with Nuvla codebase
- Knowledge of edge computing concepts

### 7.3 External Dependencies

- ETSI MEC 010-2 specification (publicly available)
- MEC test tools (if available from ETSI)
- 5G-EMERGE project requirements
- Potential access to MEC platform for integration testing

---

## 8. Next Steps

### 8.1 Immediate Actions (This Week)

1. **Kickoff Meeting**
   - Review this plan with team
   - Assign roles and responsibilities
   - Set up project tracking (Jira/GitHub)

2. **Environment Setup**
   - Create feature branch: `feature/mec-010-2`
   - Set up development environment
   - Access to MEC 010-2 specification

3. **Sprint Planning**
   - Break down Week 1-2 tasks into user stories
   - Estimate story points
   - Set up first sprint (2 weeks)

### 8.2 Week 1 Deliverables

- ✅ MEC 010-2 schema definitions
- ✅ Deployment resource extensions
- ✅ State mapping layer
- ✅ Unit tests

### 8.3 Stakeholder Communication

- **Weekly:** Status reports to 5G-EMERGE project lead
- **Bi-weekly:** Technical demos to stakeholders
- **Monthly:** Compliance progress review

---

## 9. Appendices

### Appendix A: MEC 010-2 API Endpoints Summary

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/app_lcm/v2/app_instances` | Create app instance |
| GET | `/app_lcm/v2/app_instances` | List app instances |
| GET | `/app_lcm/v2/app_instances/{id}` | Get app instance details |
| DELETE | `/app_lcm/v2/app_instances/{id}` | Delete app instance |
| POST | `/app_lcm/v2/app_instances/{id}/instantiate` | Instantiate app |
| POST | `/app_lcm/v2/app_instances/{id}/terminate` | Terminate app |
| POST | `/app_lcm/v2/app_instances/{id}/operate` | Start/stop app |
| GET | `/app_lcm/v2/app_lcm_op_occs` | List operations |
| GET | `/app_lcm/v2/app_lcm_op_occs/{id}` | Get operation details |
| POST | `/app_lcm/v2/subscriptions` | Create subscription |
| GET | `/app_lcm/v2/subscriptions` | List subscriptions |
| GET | `/app_lcm/v2/subscriptions/{id}` | Get subscription |
| DELETE | `/app_lcm/v2/subscriptions/{id}` | Delete subscription |

### Appendix B: State Mapping Reference

| Nuvla State | MEC InstantiationState | MEC OperationalState |
|-------------|------------------------|----------------------|
| CREATED | NOT_INSTANTIATED | - |
| STARTING | INSTANTIATED | - |
| STARTED | INSTANTIATED | STARTED |
| STOPPING | INSTANTIATED | - |
| STOPPED | INSTANTIATED | STOPPED |
| ERROR | INSTANTIATED | - |

### Appendix C: Useful Resources

- **ETSI MEC 010-2 Spec:** https://www.etsi.org/deliver/etsi_gs/MEC/001_099/01002/
- **RFC 7807 (ProblemDetails):** https://tools.ietf.org/html/rfc7807
- **OpenAPI 3.0:** https://swagger.io/specification/
- **Nuvla Documentation:** https://docs.nuvla.io/

---

## Change Log

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 21 Oct 2025 | GitHub Copilot | Initial implementation plan |

---

**Document Status:** Ready for Review  
**Next Review Date:** Week 5 (Checkpoint 1)  
**Owner:** 5G-EMERGE Technical Lead
