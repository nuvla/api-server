# MEC 003 Implementation Plan (MEO-Focused)
## Framework & Architecture - Nuvla as MEC Orchestrator

**Document Version:** 2.0  
**Date:** 21 October 2025  
**Project:** 5G-EMERGE / Nuvla.io  
**Scope:** Nuvla as MEO (MEC Orchestrator) only  
**Target Standard:** ETSI GS MEC 003 v3.1.1

---

## Executive Summary

This document outlines an implementation plan to align Nuvla with the **ETSI MEC 003 Framework and Reference Architecture** with Nuvla positioned as a **MEC Orchestrator (MEO)**. This simplified scope focuses on architectural conformance at the orchestration layer only.

**Scope:** MEO architectural alignment (system orchestration)  
**Current Alignment:** ~75%  
**Target Alignment:** 85-90%  
**Timeline:** 4-6 weeks  
**Team:** 1-2 developers + 0.5 architect  

**Note:** MEC 003 is primarily an architectural specification, not an API specification. Implementation focuses on system design alignment, component mapping, and creating the necessary abstractions to support other MEC standards.

---

## 1. Scope: MEC 003 for MEO

### 1.1 What is MEC 003?

**ETSI GS MEC 003** defines:

1. **System Architecture** - Overall MEC system structure and components
2. **Reference Points** - Interfaces between components (Mm1-Mm9, Mp1-Mp3)
3. **Deployment Models** - How MEC systems are structured
4. **Trust Domains** - Security boundaries and trust relationships

### 1.2 MEO-Specific Requirements

What Nuvla as MEO needs from MEC 003:

✅ **System-Level Management**
- Manage applications across multiple hosts
- Coordinate with MEPMs (MEC Platform Managers)
- Make placement decisions

✅ **Reference Points**
- **Mm5** (MEO ↔ MEPM) - Communication with platform managers
- **Mm2** (MEO ↔ VIM) - Query infrastructure resources
- **Mm3** (MEO ↔ Portal) - Customer-facing API

✅ **Application Package Management**
- On-board and validate application packages
- Distribute packages to hosts

✅ **Multi-Tenancy**
- Isolated customer environments
- Role-based access control

### 1.3 Out of Scope (Not MEO Responsibility)

❌ **Platform Services (MEP)**
- Service registry, traffic rules, DNS rules
- Mp1 interface (platform to application)

❌ **Platform Management (MEPM)**
- Host-level configuration
- Local resource monitoring

❌ **Infrastructure (VIM)**
- Container/VM runtime management
- Hardware operations

---

## 2. Current State

### 2.1 Excellent Alignment

| MEC Component | Nuvla Mapping | Fit |
|---------------|---------------|-----|
| **MEO** | Nuvla API Server | ✅ Perfect |
| **MEC Host** | NuvlaBox | ✅ Perfect |
| **Application Package** | Module | ✅ Perfect |
| **Application Instance** | Deployment | ✅ Perfect |
| **VIM** | Infrastructure Service | ✅ Good |
| **Portal (Mm3)** | Nuvla REST API + UI | ✅ Good |

### 2.2 Minor Gaps

| Gap | Current | Needed | Effort |
|-----|---------|--------|--------|
| **Mm3 Interface** | Generic REST | MEC-specific Mm5 | Medium |
| **MEPM Registry** | None | Track available MEPMs | Low |
| **MEC Terminology** | Nuvla terms | MEC aliases | Low |
| **Deployment Model Docs** | Generic | MEC-specific | Low |
| **Trust Domains** | Implicit | Explicit definition | Low |

**Overall:** ~75% aligned, mostly documentation and minor additions

---

## 3. Implementation Approach

### 3.1 Minimal Changes Philosophy

The beauty of MEO-only scope: **Most work is documentation and mapping**, not implementation.

```
┌─────────────────────────────────────────────┐
│        Nuvla Core (Unchanged)               │
│  • Existing resources and APIs              │
│  • No major architectural changes           │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│     MEC 003 Alignment Layer (NEW)           │
│  • Mm3 interface specification              │
│  • MEPM registry                            │
│  • MEC terminology mapping                  │
│  • Deployment model documentation           │
│  • Trust domain definitions                 │
└─────────────────────────────────────────────┘
```

### 3.2 Key Architecture

**Nuvla as MEO in MEC Ecosystem:**

```
┌────────────────────────────────┐
│   Nuvla MEO                    │
│   (System Orchestrator)        │
│                                │
│  • Multi-host coordination     │
│  • Application lifecycle       │
│  • Resource management         │
│  • Package management          │
└──────┬────────────────┬────────┘
       │ Mm5            │ Mm2
       │                │
┌──────▼──────┐   ┌─────▼────────┐
│ External    │   │ Infrastructure│
│ MEPM        │   │ Service (VIM) │
└──────┬──────┘   └───────────────┘
       │
┌──────▼──────┐
│ MEC Host    │
│ (NuvlaBox)  │
└─────────────┘
```

---

## 4. Implementation Phases

### Phase 1: Documentation & Mapping (Weeks 1-2)

**Objective:** Document architectural alignment with MEC 003

**Tasks:**

**Week 1: Architectural Mapping**
1. Create MEC 003 architectural mapping document
   - Map Nuvla components to MEC components
   - Document MEO role and responsibilities
   - Map reference points (Mm2, Mm3, Mm5)
   - Define deployment model

2. Create MEC terminology guide
   - MEC term → Nuvla term mappings
   - Glossary for both audiences
   - API documentation updates

3. Define trust domains
   - Operator domain (MEO, infrastructure)
   - Third-party domain (applications)
   - External domain (OSS, external services)
   - Security boundaries

**Week 2: Diagrams & Documentation**
1. Create architecture diagrams
   - Nuvla as MEO in MEC ecosystem
   - Integration with external MEPMs
   - Component relationships
   - Reference point flows

2. Document deployment models
   - Single-host deployment
   - Multi-host distributed deployment
   - Federation scenarios (future)

3. Update Nuvla documentation
   - Add MEC context to API docs
   - Reference MEC 003 standard
   - Explain MEO positioning

**Deliverables:**
- ✅ Architectural mapping document
- ✅ Component/interface mapping
- ✅ Trust domain definitions
- ✅ Architecture diagrams
- ✅ Updated API documentation

**Effort:** 40 hours (1 architect + 1 developer × 2 weeks)

---

### Phase 2: Mm3 Interface & MEPM Registry (Weeks 3-4)

**Objective:** Implement basic Mm5 protocol and MEPM registration

**Week 3: MEPM Resource**
1. Create MEPM resource definition
   - Resource schema (ID, name, endpoint, capabilities)
   - CRUD operations
   - Status tracking (online, offline, degraded)
   - Capability metadata

2. Implement MEPM registry API
   - `POST /api/mepm` - Register MEPM
   - `GET /api/mepm` - List MEPMs
   - `GET /api/mepm/{id}` - Get MEPM details
   - `PUT /api/mepm/{id}` - Update MEPM
   - `DELETE /api/mepm/{id}` - Unregister MEPM

3. MEPM capability tracking
   - Available services
   - Resource capacity
   - Supported platforms (K8s, Docker, etc.)
   - Health status

**Week 4: Mm3 Interface**
1. Define Mm5 API specification
   - Query MEPM capabilities
   - Query available resources
   - Request application instantiation
   - Query application status
   - Request termination

2. Create Mm3 client for MEPM communication
   - HTTP/REST client
   - Authentication support (API keys, OAuth2)
   - Error handling
   - Retry logic

3. Integrate with orchestration
   - Query available MEPMs for placement
   - Select MEPM based on capabilities
   - Delegate deployment operations
   - Track operation status

**Deliverables:**
- ✅ MEPM resource operational
- ✅ MEPM registry API (5 endpoints)
- ✅ Mm3 interface specification
- ✅ Mm3 client implementation
- ✅ Basic MEPM integration

**Effort:** 60 hours (1-2 developers × 2 weeks)

---

### Phase 3: Testing & Validation (Weeks 5-6)

**Objective:** Validate MEC 003 architectural alignment

**Week 5: Integration Testing**
1. Test with simulated MEPM
   - Mock MEPM for testing
   - Validate Mm5 protocol
   - Test MEPM registration/discovery
   - End-to-end orchestration flows

2. Test scenarios
   - Register multiple MEPMs
   - Query MEPM capabilities
   - Select MEPM for deployment
   - Handle MEPM failures gracefully

3. Integration with existing Nuvla
   - Verify no breaking changes
   - Test backward compatibility
   - Validate multi-host orchestration

**Week 6: Documentation & Compliance**
1. Finalize documentation
   - Compliance report (MEC 003 alignment)
   - Integration guide for MEPMs
   - API reference updates
   - Architecture decision records

2. Create deployment guide
   - How to configure Nuvla as MEO
   - How to register external MEPMs
   - Integration examples
   - Troubleshooting guide

3. Stakeholder presentation
   - Present to 5G-EMERGE team
   - Demo architectural alignment
   - Show MEPM integration
   - Gather feedback

**Deliverables:**
- ✅ Integration tests passing
- ✅ Compliance report complete
- ✅ Deployment guide ready
- ✅ Stakeholder approval

**Effort:** 40 hours (1 developer + 1 architect × 2 weeks)

---

## 5. Technical Details

### 5.1 Component Mapping

**MEC 003 → Nuvla Mapping:**

| MEC 003 Component | Nuvla Equivalent | Notes |
|-------------------|------------------|-------|
| **MEO** | Nuvla API Server | System orchestrator |
| **Application Package** | Module resource | App definitions |
| **Application Instance** | Deployment resource | Running apps |
| **Application Context** | Deployment config | Runtime parameters |
| **MEC Host** | NuvlaBox resource | Edge infrastructure |
| **MEPM** | External / NuvlaBox Agent | Platform manager |
| **VIM** | Infrastructure Service | Compute resources |

### 5.2 Reference Point Mapping

| Reference Point | Nuvla Implementation | Status |
|----------------|----------------------|--------|
| **Mm3** (MEO ↔ Portal) | REST API + UI | ✅ Existing |
| **Mm2** (MEO ↔ VIM) | Infrastructure Service API | ✅ Existing |
| **Mm5** (MEO ↔ MEPM) | New Mm3 interface | ⚠️ To implement |
| **Mm8** (MEO ↔ MEO) | Federation | ❌ Future (MEC 040) |

### 5.3 MEPM Resource Schema

**Key Attributes:**
- `id` - Unique MEPM identifier
- `name` - Human-readable name
- `endpoint` - Mm5 endpoint URL
- `mec-host-id` - Associated NuvlaBox/host
- `capabilities` - Available services, platforms
- `resources` - CPU, memory, storage capacity
- `status` - ONLINE, OFFLINE, DEGRADED
- `version` - MEPM software version

### 5.4 Mm5 Operations

**MEO → MEPM Interface:**
- `GET /mm5/capabilities` - Query what MEPM supports
- `GET /mm5/resources` - Query available resources
- `POST /mm5/app-instances` - Request app deployment
- `GET /mm5/app-instances/{id}` - Query app status
- `DELETE /mm5/app-instances/{id}` - Request termination

### 5.5 Trust Domains

**Defined Trust Zones:**

1. **Operator Domain** (High Trust)
   - Nuvla MEO
   - Infrastructure services
   - Internal monitoring

2. **Third-Party Domain** (Medium Trust)
   - External MEPMs
   - Application packages
   - Edge applications

3. **External Domain** (Low Trust)
   - External services
   - Public APIs
   - Customer systems

---

## 6. Resource Requirements

### 6.1 Effort Summary

| Phase | Duration | Hours | Team |
|-------|----------|-------|------|
| **Phase 1** | Weeks 1-2 | 40 | 1 architect + 1 developer |
| **Phase 2** | Weeks 3-4 | 60 | 1-2 developers |
| **Phase 3** | Weeks 5-6 | 40 | 1 developer + 1 architect |
| **PM Overhead** | Throughout | 20 | 0.2 PM |
| **TOTAL** | **4-6 weeks** | **160** | |

### 6.2 Team Composition

**Minimal Team:**
- 1 Solutions Architect (50% time, 6 weeks)
- 1 Senior Backend Developer (full-time, 6 weeks)
- 0.2 Project Manager (part-time)

### 6.3 Comparison

| Scope | Timeline | Effort | Complexity |
|-------|----------|--------|------------|
| **MEO Architecture Only** | 4-6 weeks | 160 hours | Very Low |
| **Full MEC 003 Implementation** | 22 weeks | 880+ hours | Medium-High |
| **Savings** | 75% faster | 82% less | Much simpler |

---

## 7. Success Criteria

### 7.1 Architectural Alignment

- ✅ All MEO components from MEC 003 mapped to Nuvla
- ✅ Required interfaces (Mm2, Mm5) documented and implemented
- ✅ Deployment model documented
- ✅ Trust domains defined and enforced

### 7.2 Functional Requirements

- ✅ MEPM resource operational
- ✅ MEPM registration and discovery working
- ✅ Mm3 interface basics functional
- ✅ Integration with at least one MEPM validated

### 7.3 Compliance Targets

| MEC 003 Section | Target Coverage |
|-----------------|-----------------|
| **Component Architecture** | 90% (MEO fully mapped) |
| **Reference Points** | 70% (MEO-relevant ones) |
| **Deployment Models** | 80% (documented) |
| **Trust Domains** | 80% (defined) |
| **Security** | 85% (existing + MEC concepts) |

**Overall Target:** 85-90% architectural alignment

---

## 8. Integration Scenarios

### 8.1 Nuvla MEO + OpenNESS Platform

```
Nuvla MEO (Orchestrator)
       ↓ Mm5
Intel OpenNESS MEPM
       ↓
OpenNESS Platform (MEP)
       ↓
Kubernetes Cluster
```

**Use Case:** Integrate with existing Intel OpenNESS infrastructure

---

### 8.2 Nuvla MEO + Multi-Vendor

```
Nuvla MEO (Unified Orchestrator)
       ↓
       ├─→ Mm5 → Vendor A MEPM
       ├─→ Mm5 → NuvlaBox MEPM
       └─→ Mm5 → Vendor B MEPM
```

**Use Case:** Multi-vendor edge environment with unified control plane

---

### 8.3 Nuvla Full Stack

```
Nuvla MEO (Orchestrator)
       ↓ Mm5
NuvlaBox Agent (enhanced as MEPM)
       ↓
NuvlaBox Platform (minimal MEP)
       ↓
Docker/K8s Runtime
```

**Use Case:** Greenfield deployment with full Nuvla control

---

## 9. Risk Management

### 9.1 Risks & Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Mm5 protocol ambiguity** | Low | Use clear REST/JSON spec, document well |
| **MEPM integration complexity** | Medium | Start with simulated MEPM, iterate |
| **Architectural misalignment** | Low | MEC 003 is well-documented |
| **Scope creep** | Low | Clear MEO-only boundaries |

**Overall Risk:** ✅ **VERY LOW**

---

## 10. Next Steps

### 10.1 Immediate Actions (This Week)

1. **Stakeholder Review**
   - Present plan to 5G-EMERGE team
   - Discuss MEO positioning
   - Get approval to proceed

2. **Resource Allocation**
   - Assign architect (50% for 6 weeks)
   - Assign developer (100% for 6 weeks)
   - Designate PM

3. **Preparation**
   - Access ETSI MEC 003 specification
   - Set up documentation templates
   - Create feature branch: `feature/mec-003-architecture`

### 10.2 Week 1-2 Deliverables

- Architectural mapping document
- Component/interface mapping
- Deployment model description
- Architecture diagrams
- Updated API documentation

### 10.3 Success Tracking

**Weekly Progress:**
- Documentation completion: X %
- MEPM resource implementation: X %
- Mm3 interface completion: X %
- Tests passing: X / Y

---

## 11. Appendices

### Appendix A: MEO Responsibilities Checklist

| MEC 003 MEO Responsibility | Nuvla Status | Action |
|---------------------------|--------------|--------|
| **System-level orchestration** | ✅ Existing | Document as MEO |
| **Multi-host coordination** | ✅ Existing | Add MEC context |
| **MEPM communication (Mm5)** | ⚠️ Generic | Implement MEC Mm5 |
| **VIM integration (Mm2)** | ✅ Existing | Document as Mm2 |
| **Customer portal (Mm3)** | ✅ Existing | Document as Mm3 |
| **Application package mgmt** | ✅ Existing | Add MEC terminology |
| **Placement decisions** | ✅ Basic | Document algorithm |

### Appendix B: Terminology Mapping

| MEC 003 Term | Nuvla Term |
|--------------|------------|
| **MEO** | Nuvla API Server |
| **MEC Application** | Deployment |
| **Application Package** | Module |
| **MEC Host** | NuvlaBox |
| **MEPM** | External / NuvlaBox Agent |
| **VIM** | Infrastructure Service |
| **Application Instance** | Deployment instance |

### Appendix C: Reference Points Summary

| Interface | Status | Implementation |
|-----------|--------|----------------|
| **Mm1** (MEO ↔ OSS) | Out of scope | Not needed |
| **Mm2** (MEO ↔ VIM) | ✅ Existing | Infrastructure Service API |
| **Mm3** (MEO ↔ Portal) | ✅ Existing | REST API + UI |
| **Mm4** (MEO ↔ UALCMP) | Out of scope | Not needed |
| **Mm5** (MEO ↔ MEPM) | ⚠️ To implement | New Mm3 interface |
| **Mm8** (MEO ↔ MEO) | Future | MEC 040 (federation) |

### Appendix D: Useful Resources

- **ETSI MEC 003:** https://www.etsi.org/deliver/etsi_gs/MEC/001_099/003/
- **MEC Wiki:** https://mecwiki.etsi.org/
- **Nuvla Docs:** https://docs.nuvla.io/

---

## Change Log

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 21 Oct 2025 | Initial full implementation plan |
| 2.0 | 21 Oct 2025 | Simplified to MEO-only scope |

---

**Document Status:** Ready for Review  
**Scope:** MEO (MEC Orchestrator) Architectural Alignment Only  
**Recommendation:** ✅ PROCEED - Low effort, high strategic value

**Suggested Approach:** Implement MEC 003 (architecture) and MEC 010-2 (APIs) in parallel over 10-12 weeks for maximum efficiency.
