# MEC 010-2 Implementation Plan (MEO-Focused)
## Application Lifecycle Management - Nuvla as MEC Orchestrator

**Document Version:** 2.0  
**Date:** 21 October 2025  
**Project:** 5G-EMERGE / Nuvla.io  
**Scope:** Nuvla as MEO (MEC Orchestrator) only  
**Target Standard:** ETSI GS MEC 010-2 v2.2.1

---

## Executive Summary

This document outlines an implementation plan for **MEC 010-2 Application Lifecycle Management APIs** with Nuvla positioned as a **MEC Orchestrator (MEO)**. This simplified scope focuses on system-level orchestration APIs, delegating platform and host management to external systems.

**Scope:** MEO-level APIs only (orchestration layer)  
**Current Alignment:** ~85% (strong foundation already exists)  
**Target Compliance:** 80-85% (excellent for MEO-only scope)  
**Timeline:** 8-10 weeks  
**Team:** 2-3 developers + 0.5 QA + 0.2 PM  

---

## 1. Scope: MEO Responsibilities

### 1.1 What Nuvla MEO Will Implement

✅ **MEC 010-2 Lifecycle APIs** (MEO-level)
- `POST /app_lcm/v2/app_instances` - Create application instance
- `GET /app_lcm/v2/app_instances` - List application instances
- `GET /app_lcm/v2/app_instances/{id}` - Get instance details
- `DELETE /app_lcm/v2/app_instances/{id}` - Delete instance
- `POST /app_lcm/v2/app_instances/{id}/instantiate` - Instantiate app
- `POST /app_lcm/v2/app_instances/{id}/terminate` - Terminate app
- `POST /app_lcm/v2/app_instances/{id}/operate` - Start/stop app

✅ **Operation Occurrence Tracking**
- `GET /app_lcm/v2/app_lcm_op_occs` - List operations
- `GET /app_lcm/v2/app_lcm_op_occs/{id}` - Get operation status

✅ **Application Package Management**
- On-board application descriptors (modules)
- Validate application packages
- Version management

✅ **Multi-Host Orchestration**
- Placement decisions (which host for each app)
- Resource-aware scheduling
- Multi-host coordination

✅ **Mm3 Interface (MEO ↔ MEPM)**
- Query MEPM capabilities
- Delegate instantiation requests
- Monitor application status

### 1.2 Out of Scope (Delegated to External Systems)

❌ **Platform Services (handled by external MEP)**
- Service registry
- Traffic rules engine
- DNS rules configuration
- Mp1 application enablement interface

❌ **Host Management (handled by external MEPM)**
- Local resource monitoring
- Container/VM lifecycle
- Host-level networking

❌ **Infrastructure (handled by VIM)**
- Kubernetes/Docker runtime
- Storage/network provisioning

---

## 2. Current State Assessment

### 2.1 Strong Foundation

| Component | Current Status | MEO Relevance |
|-----------|----------------|---------------|
| **Deployment Resource** | ✅ Mature | Maps to AppInstance |
| **Module Resource** | ✅ Mature | Maps to AppPackage |
| **Job Resource** | ✅ Mature | Maps to AppLcmOpOcc |
| **Event Resource** | ✅ Mature | Notifications |
| **NuvlaBox Resource** | ✅ Mature | MEC Hosts |
| **Infrastructure Service** | ✅ Mature | VIM interface |

**Key Strengths:**
- Multi-host orchestration already works
- Lifecycle operations fully functional
- Event-driven architecture in place
- Strong authentication & authorization
- RESTful API framework ready

### 2.2 Minor Gaps to Address

| Gap | Current | Needed | Effort |
|-----|---------|--------|--------|
| **MEC API Format** | Nuvla REST | MEC 010-2 schema | Low |
| **State Names** | Nuvla states | MEC states | Low |
| **AppLcmOpOcc** | Generic job | MEC-specific tracking | Low |
| **Mm5 Protocol** | Generic REST | MEC Mm5 operations | Medium |
| **Error Format** | Custom JSON | ProblemDetails (RFC 7807) | Low |

**Overall:** ~85% aligned, minimal work needed

---

## 3. Implementation Approach

### 3.1 Layered Design

```
┌─────────────────────────────────────────────────────┐
│     Layer 3: MEC 010-2 API Facade                   │
│  • /app_lcm/v2/* endpoints                          │
│  • Request/response translation                     │
│  • MEC schema validation                            │
└────────────────────┬────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────┐
│     Layer 2: MEO Orchestration Logic                │
│  • Placement algorithm                              │
│  • Multi-host coordination                          │
│  • MEPM delegation (Mm5)                            │
└────────────────────┬────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────┐
│     Layer 1: Existing Nuvla Resources               │
│  • deployment, module, job, event                   │
│  • No changes to core resources                     │
└─────────────────────────────────────────────────────┘
```

### 3.2 Key Patterns

**Pattern 1: API Facade**
- MEC 010-2 endpoints translate to Nuvla operations
- No changes to existing deployment/module resources
- Adapter layer handles format conversion

**Pattern 2: State Mapping**
- Nuvla states (CREATED, STARTED, STOPPED) → MEC states (NOT_INSTANTIATED, INSTANTIATED)
- Bidirectional mapping for compatibility

**Pattern 3: MEPM Delegation**
- MEO selects target host (placement algorithm)
- MEO delegates to MEPM via Mm3 interface
- MEPM handles actual deployment on host

---

## 4. Implementation Phases

### Phase 1: Core API & Translation (Weeks 1-3)

**Objective:** MEC 010-2 API endpoints operational

**Week 1: Schema & Data Models**
- Define MEC 010-2 schemas (AppInstanceInfo, AppLcmOpOcc)
- Create state mapping functions
- Document field mappings (deployment ↔ AppInstanceInfo)

**Week 2: API Endpoints**
- Implement CRUD operations:
  - `POST /app_lcm/v2/app_instances`
  - `GET /app_lcm/v2/app_instances`
  - `GET /app_lcm/v2/app_instances/{id}`
  - `DELETE /app_lcm/v2/app_instances/{id}`
- Add request validation
- Implement response translation

**Week 3: Testing**
- Unit tests for data translation
- API integration tests
- State mapping validation

**Deliverables:**
- ✅ MEC API namespace created
- ✅ 4 CRUD endpoints working
- ✅ Translation layer functional
- ✅ Basic tests passing

**Effort:** 120 hours (2 developers × 3 weeks)

---

### Phase 2: Lifecycle Operations (Weeks 4-6)

**Objective:** Instantiate, terminate, operate actions working

**Week 4: Lifecycle Endpoints**
- `POST /app_lcm/v2/app_instances/{id}/instantiate`
- `POST /app_lcm/v2/app_instances/{id}/terminate`
- `POST /app_lcm/v2/app_instances/{id}/operate`
- Link to existing job system

**Week 5: Operation Tracking**
- Create AppLcmOpOcc resource (extends job)
- Track operation state transitions
- Implement operation query endpoints:
  - `GET /app_lcm/v2/app_lcm_op_occs`
  - `GET /app_lcm/v2/app_lcm_op_occs/{id}`

**Week 6: Mm5 Basics**
- Define Mm5 protocol for MEPM communication
- Create MEPM resource (registry)
- Implement MEPM capability queries
- Delegate instantiation to MEPM

**Deliverables:**
- ✅ 3 lifecycle operation endpoints
- ✅ Operation occurrence tracking
- ✅ Basic Mm3 interface
- ✅ MEPM registry functional

**Effort:** 120 hours (2 developers × 3 weeks)

---

### Phase 3: Orchestration & Polish (Weeks 7-10)

**Objective:** Multi-host orchestration, error handling, production-ready

**Week 7: Placement Algorithm**
- Implement host selection logic
- Score hosts based on:
  - Resource availability (CPU, memory, storage)
  - Network latency
  - Current load
  - Affinity/anti-affinity rules
- Select optimal host for each app

**Week 8: Multi-Host Coordination**
- Cross-host application tracking
- Dependency resolution
- Load balancing across hosts

**Week 9: Error Handling**
- Implement ProblemDetails format (RFC 7807)
- Define error type taxonomy
- Add retry and rollback logic
- Comprehensive error responses

**Week 10: Documentation & Testing**
- OpenAPI 3.0 specification
- API documentation
- End-to-end testing
- Integration guide for MEPMs
- Compliance validation

**Deliverables:**
- ✅ Smart placement algorithm
- ✅ Multi-host orchestration
- ✅ Robust error handling
- ✅ Complete documentation
- ✅ Production-ready system

**Effort:** 160 hours (2-3 developers × 3.5 weeks)

---

## 5. Technical Details

### 5.1 Data Model Mapping

**MEC AppInstanceInfo ↔ Nuvla Deployment**

| MEC Field | Nuvla Field | Notes |
|-----------|-------------|-------|
| `appInstanceId` | `id` | Direct mapping |
| `appDId` | `module-id` | Application descriptor |
| `appName` | `module/name` | From module resource |
| `instantiationState` | `state` (mapped) | NOT_INSTANTIATED / INSTANTIATED |
| `operationalState` | `state` (derived) | STARTED / STOPPED |
| `mecHostInformation` | `parent` (NuvlaBox) | Host details |
| `_links` | Generated | HATEOAS links |

**State Mapping:**

| Nuvla State | MEC Instantiation | MEC Operational |
|-------------|-------------------|-----------------|
| CREATED | NOT_INSTANTIATED | - |
| STARTING | INSTANTIATED | - |
| STARTED | INSTANTIATED | STARTED |
| STOPPING | INSTANTIATED | - |
| STOPPED | INSTANTIATED | STOPPED |
| ERROR | INSTANTIATED | - |

### 5.2 Placement Algorithm

**Host Selection Process:**
1. Extract application requirements from descriptor
2. Filter hosts that meet minimum requirements
3. Score remaining hosts:
   - **40%** Resource availability
   - **30%** Network latency
   - **20%** Current load
   - **10%** Affinity rules
4. Select highest-scoring host
5. Delegate to host's MEPM via Mm5

### 5.3 Mm3 Interface

**MEO → MEPM Operations:**
- `GET /mm5/capabilities` - Query MEPM capabilities
- `GET /mm5/resources` - Query available resources
- `POST /mm5/app-instances` - Request app instantiation
- `GET /mm5/app-instances/{id}` - Query app status
- `DELETE /mm5/app-instances/{id}` - Request termination

**MEPM can be:**
- External MEC platform (e.g., OpenNESS)
- Enhanced NuvlaBox agent
- Third-party edge platform

---

## 6. Resource Requirements

### 6.1 Effort Summary

| Phase | Duration | Hours | Team |
|-------|----------|-------|------|
| **Phase 1** | Weeks 1-3 | 120 | 2 developers |
| **Phase 2** | Weeks 4-6 | 120 | 2 developers |
| **Phase 3** | Weeks 7-10 | 160 | 2-3 developers |
| **Testing & QA** | Throughout | 80 | 0.5 QA engineer |
| **PM Overhead** | Throughout | 40 | 0.2 PM |
| **TOTAL** | **8-10 weeks** | **520** | |

### 6.2 Team Composition

**Minimal Team:**
- 2 Senior Backend Developers
- 0.5 QA Engineer (part-time)
- 0.2 Project Manager (part-time)

### 6.3 Comparison

| Scope | Timeline | Effort | Complexity |
|-------|----------|--------|------------|
| **MEO Only** | 8-10 weeks | 520 hours | Low-Medium |
| **Full MEC Platform** | 30-38 weeks | 2,200+ hours | High |
| **Savings** | 70-75% faster | 76% less | Much simpler |

---

## 7. Success Criteria

### 7.1 Functional Requirements

- ✅ All MEC 010-2 MEO-level APIs implemented
- ✅ AppInstance lifecycle operations working
- ✅ Operation occurrence tracking functional
- ✅ Multi-host orchestration operational
- ✅ Mm3 interface for MEPM delegation
- ✅ Placement algorithm making smart decisions

### 7.2 Compliance Targets

| MEC 010-2 Section | Target Coverage | Notes |
|-------------------|----------------|-------|
| **Application Lifecycle APIs** | 90% | All MEO-level operations |
| **AppInstanceInfo** | 85% | Core fields implemented |
| **AppLcmOpOcc** | 80% | Essential states tracked |
| **Queries & Filters** | 70% | Basic filtering supported |
| **Error Handling** | 90% | ProblemDetails format |

**Overall Target:** 80-85% compliance (excellent for MEO-only scope)

### 7.3 Quality Gates

**Week 3 Checkpoint:**
- Core API endpoints responding
- Data translation working
- Basic tests passing

**Week 6 Checkpoint:**
- Lifecycle operations functional
- Operation tracking working
- Mm5 basics implemented

**Week 10 Final:**
- All tests passing (>85% coverage)
- Documentation complete
- Integration validated with at least one MEPM
- Production deployment ready

---

## 8. Integration Scenarios

### 8.1 Nuvla MEO + External MEC Platform

```
User Application
       ↓
Nuvla MEO (MEC 010-2 API)
       ↓ Mm5
External MEPM (e.g., OpenNESS)
       ↓
MEC Platform (MEP)
       ↓
Application Running
```

### 8.2 Nuvla MEO + Enhanced NuvlaBox

```
User Application
       ↓
Nuvla MEO (MEC 010-2 API)
       ↓ Mm5
NuvlaBox Agent (MEPM role)
       ↓
NuvlaBox Platform
       ↓
Docker/K8s Deployment
```

### 8.3 Nuvla MEO + Multi-Vendor

```
User Application
       ↓
Nuvla MEO (MEC 010-2 API)
       ↓
       ├─→ Mm5 → MEPM Vendor A
       ├─→ Mm5 → NuvlaBox MEPM
       └─→ Mm5 → MEPM Vendor B
```

---

## 9. Risk Management

### 9.1 Technical Risks

| Risk | Mitigation |
|------|------------|
| **Mm5 protocol ambiguity** | Use clear REST/JSON specification, document well |
| **MEPM integration complexity** | Start with simulated MEPM, add real ones incrementally |
| **State synchronization** | Event-driven updates, eventual consistency model |

### 9.2 Organizational Risks

| Risk | Mitigation |
|------|------------|
| **Scope creep** | Strict MEO-only boundaries, clear what's out of scope |
| **Resource availability** | Small team requirement, short timeline |

**Overall Risk:** ✅ **LOW** - Well-defined scope, existing foundation

---

## 10. Next Steps

### 10.1 Immediate Actions

1. **Stakeholder Approval**
   - Review plan with 5G-EMERGE team
   - Confirm MEO-only scope
   - Get approval to proceed

2. **Team Setup**
   - Assign 2 senior developers
   - Secure QA resources (part-time)
   - Designate project manager

3. **Environment Preparation**
   - Create feature branch: `feature/mec-010-2-meo`
   - Set up development environment
   - Access to MEC 010-2 specification

### 10.2 Week 1 Kickoff

**Day 1-2:**
- Team onboarding
- MEC 010-2 specification review (MEO sections)
- Sprint planning

**Day 3-5:**
- Create MEC namespace structure
- Define schemas
- First endpoint implementation

### 10.3 Success Tracking

**Weekly Metrics:**
- API endpoints implemented: X / 9
- Test coverage: X %
- Integration tests passing: X / Y
- Documentation completion: X %

---

## 11. Appendices

### Appendix A: API Endpoints Summary

| Endpoint | Purpose | Phase |
|----------|---------|-------|
| `POST /app_lcm/v2/app_instances` | Create instance | Phase 1 |
| `GET /app_lcm/v2/app_instances` | List instances | Phase 1 |
| `GET /app_lcm/v2/app_instances/{id}` | Get instance | Phase 1 |
| `DELETE /app_lcm/v2/app_instances/{id}` | Delete instance | Phase 1 |
| `POST /app_lcm/v2/app_instances/{id}/instantiate` | Instantiate app | Phase 2 |
| `POST /app_lcm/v2/app_instances/{id}/terminate` | Terminate app | Phase 2 |
| `POST /app_lcm/v2/app_instances/{id}/operate` | Start/stop app | Phase 2 |
| `GET /app_lcm/v2/app_lcm_op_occs` | List operations | Phase 2 |
| `GET /app_lcm/v2/app_lcm_op_occs/{id}` | Get operation | Phase 2 |

### Appendix B: Out of Scope (Not MEO Responsibility)

❌ Service Registry (MEP responsibility)  
❌ Traffic Rules Engine (MEP responsibility)  
❌ DNS Rules Engine (MEP responsibility)  
❌ Mp1 Interface (MEP ↔ App, not MEO)  
❌ Host-level monitoring (MEPM responsibility)  
❌ Container runtime management (VIM responsibility)

### Appendix C: Useful Resources

- **ETSI MEC 010-2:** https://www.etsi.org/deliver/etsi_gs/MEC/001_099/01002/
- **RFC 7807 (ProblemDetails):** https://tools.ietf.org/html/rfc7807
- **OpenAPI 3.0:** https://swagger.io/specification/
- **Nuvla Documentation:** https://docs.nuvla.io/

---

## Change Log

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 21 Oct 2025 | Initial full implementation plan |
| 2.0 | 21 Oct 2025 | Simplified to MEO-only scope |

---

**Document Status:** Ready for Review  
**Scope:** MEO (MEC Orchestrator) Only  
**Recommendation:** ✅ PROCEED - High value, low risk, well-defined scope
