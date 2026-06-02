# Compliance Study of Nuvla Edge Orchestration Solution
## Assessment of Compliance with 3GPP Edge Computing Standards

**Version:** 1.0  
**Date:** October 2025  
**Standards:** 3GPP TS 23.558 v19.6.0, TS 23.501 v19.5.0, TS 28.538 v17.4.0  
**Scope:** Edge Application Enablement and Management

---

## Executive Summary

This document assesses Nuvla's compliance with **3GPP edge computing standards** for operating as an **Edge Application Management system** integrated with 5G networks. Unlike ETSI MEC which focuses on MEC Orchestrator (MEO) functionality, 3GPP defines edge computing through the **Edge Application Enablement Layer (EDGEAPP)** with complementary interfaces to the 5G System.

**Key 3GPP Edge Components:**
- **Edge Enabler Server (EES)**: Discovers, authorizes, and manages edge applications
- **Edge Configuration Server (ECS)**: Provisions and configures edge application clients
- **Edge Application Server (EAS)**: Hosts edge applications at network edge
- **Application Function (AF)**: Interfaces with 5G Core for network services

**Target:** Minimum Viable Edge Enablement Platform (Phase 1 + Phase 2) - integration-ready with 5G networks.

**Key Finding:** Nuvla has strong foundational capabilities but requires significant extensions to support 3GPP-specific edge enablement APIs, 5G Core integration, and standardized edge discovery/provisioning mechanisms.

---

## 1. 3GPP Edge Computing Architecture Overview

### 1.1 Architecture Components (TS 23.558 §4.2)

3GPP defines edge computing through an **Enablement Layer** that sits between applications and the 5G System:

```
┌──────────────────────────────────────────────────────────┐
│                    Application Layer                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐      │
│  │   AC (App   │  │   AC (App   │  │   AC (App   │      │
│  │   Client)   │  │   Client)   │  │   Client)   │      │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘      │
└─────────┼─────────────────┼─────────────────┼────────────┘
          │ EDGE-1          │ EDGE-1          │ EDGE-1
          │                 │                 │
┌─────────▼─────────────────▼─────────────────▼────────────┐
│           Edge Application Enablement Layer               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │     ECS      │  │     EES      │  │     EAS      │   │
│  │  (Config)    │  │  (Enabler)   │  │  (App Host)  │   │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘   │
└─────────┼──────────────────┼──────────────────┼───────────┘
          │ EES-ECS          │ Nees_Application │
          │                  │                  │
          │                  ▼ Nnef             ▼ N33
┌─────────▼──────────────────────────────────────────────┐
│                     5G System (5GC)                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│  │   NEF    │  │   AF     │  │   PCF    │            │
│  │(Exposure)│  │(App Func)│  │ (Policy) │            │
│  └──────────┘  └──────────┘  └──────────┘            │
└────────────────────────────────────────────────────────┘
```

**Key Interfaces:**
- **EDGE-1**: Application Client (AC) ↔ EES/ECS (edge discovery, configuration)
- **EDGE-2**: EES ↔ EAS (application enablement, lifecycle)
- **EDGE-3**: EAS ↔ Application Client (application data)
- **EES-ECS**: EES ↔ ECS (coordination)
- **Nnef**: EES ↔ NEF (5G Core exposure, network capabilities)
- **Naf**: EAS/AF ↔ 5GC (policy, QoS, charging)

### 1.2 Key Functional Entities

**Edge Enabler Server (EES) - TS 23.558 §6.2:**
- Edge application discovery and selection
- Edge application context and configuration provisioning
- Application Client (AC) registration and authorization
- Dynamic DNS (ADRF - Access Traffic Routing Function)
- Service provisioning information to UEs
- Integration with 5G Core via NEF

**Edge Configuration Server (ECS) - TS 23.558 §6.3:**
- Edge application client profile management
- Configuration provisioning to application clients
- Application Context Transfer (ACT) coordination
- Service continuity support

**Edge Application Server (EAS) - TS 23.558 §6.4:**
- Hosts edge applications
- Provides edge services to application clients
- Dynamic relocation based on UE mobility
- Integration with 5G Core for QoS, policies

**Application Function (AF) - TS 23.501 §6.2.6:**
- Interacts with 5G Core (PCF, NEF, UDM)
- Requests network services (QoS, exposure, analytics)
- Subscribes to events (mobility, location, QoS changes)

---

## 2. 3GPP Edge Requirements for Nuvla

### Priority 1: Critical (Minimum Viable Edge Platform)

#### R1 - Edge Enabler Server (EES) Functionality (TS 23.558 §7.2)

**Core Requirements:**
- **Application Discovery (EDGE-1)**: REST API for AC to discover available edge applications
  - Input: Application ID, location, service area
  - Output: EAS endpoint(s), service details, service area info
  - Support for DNS-based and API-based discovery
- **Application Context Provisioning**: Provide application-specific configuration to ACs
- **EAS Selection**: Select optimal EAS based on location, load, capabilities
- **Service Continuity**: Track AC location changes, trigger EAS relocation when needed
- **Dynamic DNS (ADRF)**: Resolve application FQDNs to edge-optimal EAS IPs

**Data Models (TS 23.558 Annex B):**
- `EdgeAppInfo`: Application ID, name, version, service area, EAS endpoints
- `ACProfile`: AC identifier, location, subscribed applications
- `ServiceProvisioningInfo`: EAS endpoint, DNS configuration, context data

**APIs Required (6 endpoints):**
1. `POST /ees/discovery` - Discover edge applications
2. `GET /ees/applications/{appId}` - Get application details
3. `POST /ees/context-provisioning` - Provision AC context
4. `GET /ees/service-area` - Query service area information
5. `POST /ees/dns-resolution` - Dynamic DNS resolution
6. `POST /ees/subscriptions` - Subscribe to EAS changes/events

**Effort:** 6-8 weeks (new functionality, 5G integration complexity)

---

#### R2 - Edge Configuration Server (ECS) Functionality (TS 23.558 §7.3)

**Core Requirements:**
- **Configuration Management**: Store and provide AC profiles and configurations
- **Application Context Transfer (ACT)**: Coordinate state transfer between EAS instances
- **AC Registration**: Register application clients with authorized configurations
- **Configuration Updates**: Push configuration updates to registered ACs

**Data Models:**
- `ACConfiguration`: AC profile, allowed EAS, policies, QoS parameters
- `ACTInfo`: Transfer state, source EAS, target EAS, context data

**APIs Required (5 endpoints):**
1. `POST /ecs/registration` - Register application client
2. `GET /ecs/configuration/{acId}` - Retrieve AC configuration
3. `PUT /ecs/configuration/{acId}` - Update AC configuration
4. `POST /ecs/act-initiate` - Initiate application context transfer
5. `GET /ecs/act-status/{actId}` - Query ACT status

**Effort:** 4-5 weeks (moderate complexity, state management)

---

#### R3 - 5G Core Integration via NEF (TS 23.501 §5.6, TS 29.522)

**Core Requirements:**
- **NEF Client**: HTTP/2 REST client to interact with 5G Network Exposure Function
- **Service Discovery**: Query 5G network capabilities (analytics, QoS, events)
- **Event Subscriptions**: Subscribe to UE mobility, location, QoS monitoring events
- **Application QoS**: Request application-level QoS guarantees for edge traffic
- **Analytics Consumption**: Retrieve network analytics (UE mobility predictions, load)

**NEF APIs (TS 29.522 - Selected):**
- `Nnef_EventExposure`: Subscribe to network events (UE mobility, location changes)
- `Nnef_AnalyticsExposure`: Consume network analytics
- `Nnef_PFDManagement`: Manage Packet Flow Descriptions
- `Nnef_TrafficInfluence`: Request traffic steering to edge

**Operations Required:**
1. NEF authentication (OAuth 2.0)
2. Event subscription creation/deletion
3. Event notification webhook handling
4. Analytics query execution
5. Traffic influence requests

**Effort:** 5-7 weeks (complex, requires 5G Core access, security)

---

#### R4 - Application Function (AF) Integration (TS 23.501 §5.6.7)

**Core Requirements:**
- **Policy Coordination (N5 Interface)**: Request policies from PCF for application sessions
- **QoS Flow Management**: Request guaranteed QoS for edge application traffic
- **Charging Correlation**: Provide charging identifiers for application usage
- **Service Data Flow (SDF) Management**: Define traffic filters for edge applications

**AF APIs (TS 29.514, TS 29.522):**
- `Npcf_PolicyAuthorization`: Request application session policies
- `Naf_EventExposure`: Subscribe to application session events
- Session establishment/modification/termination

**Effort:** 4-6 weeks (moderate complexity, requires PCF connectivity)

---

### Priority 2: Important (Production Edge Platform)

#### R5 - Application Lifecycle Management for EAS (TS 28.538)

**Core Requirements:**
- **EAS Deployment**: Deploy, scale, terminate EAS instances dynamically
- **EAS Monitoring**: Health checks, resource utilization, performance metrics
- **EAS Discovery Registry**: Maintain registry of available EAS instances
- **Multi-Site Orchestration**: Coordinate EAS across multiple edge sites

**Operations:**
- Deploy EAS (container/VM) to edge compute nodes
- Scale EAS horizontally based on load
- Migrate EAS across edge sites for service continuity
- Decommission EAS instances

**Effort:** 5-6 weeks (builds on existing Nuvla deployment capabilities)

---

#### R6 - Service Continuity and Application Context Transfer (TS 23.558 §7.5)

**Core Requirements:**
- **Seamless Handover**: Transfer application state when AC moves between EAS
- **Context State Management**: Serialize/deserialize application state
- **ACT Coordination**: EES-initiated transfer between source and target EAS
- **Failure Handling**: Rollback, retry logic for failed transfers

**ACT Flow:**
1. EES detects AC location change (via NEF event)
2. EES selects new target EAS closer to AC
3. EES initiates ACT with source and target EAS
4. Source EAS serializes application state
5. Target EAS receives state, prepares to serve AC
6. EES updates AC with new EAS endpoint

**Effort:** 6-8 weeks (complex, requires state management, coordination)

---

#### R7 - Edge Analytics and Telemetry (TS 23.558 §7.7)

**Core Requirements:**
- **EAS Performance Metrics**: Latency, throughput, resource usage
- **Application Analytics**: Usage patterns, active sessions, request counts
- **Network Analytics Integration**: Consume 5G network analytics from NEF/NWDAF
- **Predictive Optimization**: Use analytics for proactive EAS selection/relocation

**Metrics to Collect:**
- EAS response time (P50, P95, P99)
- Active application sessions
- Resource utilization (CPU, memory, network)
- UE-to-EAS distance/latency
- Context transfer success rate

**Effort:** 3-4 weeks (moderate, telemetry infrastructure)

---

#### R8 - Edge Security and Authentication (TS 33.558)

**Core Requirements:**
- **AC Authentication**: OAuth 2.0 / OIDC for application client authentication
- **API Security (API-GW)**: TLS 1.3, mTLS for EES/ECS APIs
- **Authorization Policies**: Role-based access control for edge applications
- **Token Management**: JWT issuance, validation, refresh

**Security Features:**
- TLS 1.3 for all EDGE-1, EDGE-2, NEF interfaces
- Mutual TLS (mTLS) for service-to-service communication
- OAuth 2.0 authorization flows
- API rate limiting and DDoS protection

**Effort:** 4-5 weeks (security hardening, OAuth/OIDC integration)

---

### Priority 3: Advanced (Future Enhancements)

**R9 - Multi-Access Edge Computing (MEC + 3GPP Convergence)**: Interoperability between ETSI MEC and 3GPP EDGEAPP (6-8 weeks)

**R10 - Network Slicing Integration**: Deploy edge applications within specific 5G network slices (5-6 weeks)

**R11 - AI/ML-Driven EAS Placement**: Use ML models for intelligent EAS selection and relocation (8-10 weeks)

**R12 - Multi-Operator Scenarios**: Support roaming, multi-operator edge access (6-8 weeks)

---

## 3. Gap Analysis

Nuvla has strong foundational capabilities but **significant gaps** in 3GPP-specific edge enablement:

### Critical Gaps (Block 3GPP Compliance)

| Gap | Requirement | Current State | Effort | Priority |
|-----|-------------|---------------|--------|----------|
| **Gap 1** | EES APIs (6 endpoints, discovery, provisioning) | ❌ Not implemented | 6-8 weeks | 🔴 CRITICAL |
| **Gap 2** | ECS APIs (5 endpoints, config, ACT) | ❌ Not implemented | 4-5 weeks | 🔴 CRITICAL |
| **Gap 3** | NEF Integration (event subscriptions, analytics) | ❌ No 5G Core connectivity | 5-7 weeks | 🔴 CRITICAL |
| **Gap 4** | AF Integration (policy, QoS requests) | ❌ No AF functionality | 4-6 weeks | 🔴 CRITICAL |

**Phase 1 Total:** 19-26 weeks

### Important Gaps (Limit Production Use)

| Gap | Requirement | Current State | Effort | Priority |
|-----|-------------|---------------|--------|----------|
| **Gap 5** | EAS Lifecycle (deploy, monitor, scale) | ✅ Partial (Nuvla deployments) | 5-6 weeks | 🟡 HIGH |
| **Gap 6** | Service Continuity (ACT, handover) | ❌ No state transfer | 6-8 weeks | 🟡 HIGH |
| **Gap 7** | Edge Analytics (metrics, telemetry) | ✅ Partial (Nuvla metrics) | 3-4 weeks | 🟡 MEDIUM |
| **Gap 8** | Security (OAuth, mTLS, API-GW) | ✅ Partial (Nuvla ACL, auth) | 4-5 weeks | 🟡 MEDIUM |

**Phase 2 Total:** 18-23 weeks

### Existing Nuvla Capabilities (Leverage)

| Capability | Relevance to 3GPP | Adaptation Needed |
|------------|-------------------|-------------------|
| **Module Resources** | Package management for EAS | ✅ Map to EdgeAppInfo |
| **Deployment Resources** | EAS deployment, lifecycle | ✅ Extend with EAS-specific logic |
| **NuvlaEdge Resources** | Edge host inventory | ✅ Integrate with EAS registry |
| **Job System** | Operation tracking | ✅ Track ACT operations |
| **Event System** | Notifications | ✅ Map to NEF event subscriptions |
| **ACL System** | Multi-tenancy, RBAC | ✅ Extend with OAuth/OIDC |
| **Metrics & Telemetry** | Resource monitoring | ✅ Add EAS performance metrics |

---

## 4. Implementation Roadmap

### Phase 1: Core 3GPP Edge Enablement (19-26 weeks)

**Goal:** Implement EES, ECS, and basic 5G Core integration

**Deliverables:**
1. **EES Implementation (6-8 weeks)**
   - 6 REST API endpoints (discovery, provisioning, DNS, subscriptions)
   - Data models: EdgeAppInfo, ACProfile, ServiceProvisioningInfo
   - EAS selection algorithm (location-based, basic load balancing)
   - 50+ unit tests, 10+ integration tests

2. **ECS Implementation (4-5 weeks)**
   - 5 REST API endpoints (registration, configuration, ACT)
   - Data models: ACConfiguration, ACTInfo
   - AC profile management, configuration provisioning
   - 40+ tests

3. **NEF Integration (5-7 weeks)**
   - HTTP/2 client for NEF APIs (TS 29.522)
   - Event subscription: UE mobility, location, QoS monitoring
   - Webhook handling for NEF notifications
   - OAuth 2.0 authentication
   - 30+ tests, NEF simulator for testing

4. **AF Integration (4-6 weeks)**
   - Policy coordination with PCF (Npcf_PolicyAuthorization)
   - QoS flow requests
   - Charging correlation
   - 25+ tests, PCF simulator

**Success Criteria:**
- ✅ EES: 6 endpoints operational, edge application discovery working
- ✅ ECS: 5 endpoints operational, AC registration and configuration working
- ✅ NEF: Event subscriptions functional, receive UE mobility notifications
- ✅ AF: Policy requests successful, QoS flow established
- ✅ 145+ tests passing, 75%+ coverage
- ✅ End-to-end flow: AC discovers EAS via EES, receives configuration from ECS

---

### Phase 2: Production 3GPP Edge Platform (18-23 weeks)

**Goal:** Add lifecycle management, service continuity, analytics, security

**Deliverables:**
1. **EAS Lifecycle Management (5-6 weeks)**
   - Deploy EAS as containers to edge sites
   - Monitor EAS health, performance, resource usage
   - Scale EAS dynamically based on load
   - EAS registry with discovery API
   - 40+ tests

2. **Service Continuity & ACT (6-8 weeks)**
   - Application Context Transfer coordination
   - State serialization/deserialization framework
   - EES-initiated handover based on UE mobility
   - Failure handling, rollback, retries
   - 50+ tests

3. **Edge Analytics & Telemetry (3-4 weeks)**
   - Collect EAS metrics (latency, throughput, resource usage)
   - Integrate with 5G network analytics (NEF/NWDAF)
   - Predictive EAS selection using analytics
   - Grafana dashboards for edge telemetry
   - 20+ tests

4. **Security Hardening (4-5 weeks)**
   - OAuth 2.0 / OIDC for AC authentication
   - mTLS for service-to-service communication
   - API Gateway with rate limiting, DDoS protection
   - JWT token management
   - Security audit, penetration testing
   - 30+ tests

**Success Criteria:**
- ✅ EAS deployed dynamically to edge sites, scaled based on load
- ✅ ACT functional: application state transferred during handover
- ✅ Analytics: EAS performance metrics collected, predictive placement working
- ✅ Security: OAuth/OIDC authentication, mTLS enabled
- ✅ 140+ tests passing, 80%+ coverage
- ✅ End-to-end: AC registers, discovers EAS, receives optimized endpoint, seamless handover on mobility

---

### Phase 3: Advanced Features (Future, 6-12 months)

**Scope:** MEC-3GPP convergence, network slicing, AI/ML placement, multi-operator support - **Deferred**

---

## 5. 3GPP Certification & Validation

**Validation Requirements:**
1. **Functional Testing**: All EES, ECS APIs functional per TS 23.558
2. **5G Core Integration Testing**: NEF, AF interfaces operational with live 5GC or simulator
3. **Interoperability Testing**: EES/ECS interop with 3rd-party EAS, ACs
4. **Performance Testing**: EAS discovery <100ms, ACT <500ms, support 1000+ concurrent ACs
5. **Security Testing**: OAuth/OIDC flows, mTLS handshake, API security audit

**Certification Path:**
- 3GPP does not have formal certification like ETSI MEC
- Validation via **conformance testing** against 3GPP Test Specifications (TS 34.xxx, TS 36.xxx)
- Participation in **3GPP Plugfests** for interoperability validation
- Operator acceptance testing with live 5G networks

**Timeline:** Validation phase: 4-6 weeks (after Phase 1 + 2 complete)

---

## 6. Resource Requirements

### Development (Phase 1 + 2)

**Team:**
- 2-3 senior developers (full-time, expertise in 5G, edge, cloud-native)
- 0.5 QA engineer (test automation, 5G testing)
- 0.3 technical writer (API docs, integration guides)
- 0.3 DevOps engineer (5G Core simulator, CI/CD)

**Effort:** 37-49 person-weeks

**Budget:** €259,000 - €343,000 (development) + €2,500/month (infrastructure: 5G Core simulator, edge sites)

### Infrastructure

**Development:**
- 5G Core Network Simulator (Open5GS, free5GC) - for NEF/AF testing
- Edge compute nodes (3-5 VMs/containers) - for EAS deployment
- Test UE simulators - for mobility/handover testing
- CI/CD pipeline - automated testing

**Production:**
- 5G Core integration (NEF, AF connectivity) - provided by operator
- Edge sites (multi-site deployment) - existing infrastructure
- OAuth/OIDC provider (Keycloak, Auth0) - authentication
- Monitoring stack (Prometheus, Grafana) - telemetry

---

## 7. Risk Assessment

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| 5G Core access unavailable | High | Medium | Use Open5GS/free5GC simulators for dev/test |
| NEF/AF interface changes | Medium | Low | Follow 3GPP Rel-17/18 specs, version compatibility |
| Complexity underestimated | High | Medium | 25% buffer, phased approach, weekly reviews |
| ACT state transfer complexity | High | Medium | Start with stateless apps, add stateful later |
| Multi-operator scenarios | Medium | Low | Defer to Phase 3, focus on single operator |
| Limited 3GPP expertise | High | Medium | Training, 3GPP spec study, consultant support |

---

## 8. Success Metrics

**Technical:**
- 90%+ 3GPP TS 23.558 compliance (EES, ECS APIs)
- 80%+ test coverage, 185+ unit tests, 20+ integration tests
- <100ms EAS discovery latency (P95)
- <500ms Application Context Transfer time (P95)
- 99.9% EES/ECS API uptime

**Business:**
- Integration with 1+ live 5G network operator
- Deployment in 5G-EMERGE project with 5G Core connectivity
- Participation in 3GPP Plugfest (interoperability validation)
- Public documentation and integration guides

---

## 9. Comparison: 3GPP vs. ETSI MEC

| Aspect | 3GPP EDGEAPP | ETSI MEC | Nuvla Strategy |
|--------|--------------|----------|----------------|
| **Focus** | 5G-native edge enablement | Telco-agnostic edge orchestration | **Both**: MEC for general edge, 3GPP for 5G |
| **Key Component** | EES (Enabler Server) | MEO (Orchestrator) | Implement EES + MEO |
| **5G Integration** | Native (NEF, AF) | Optional (via Mm5) | 3GPP for tight 5G integration |
| **Application Model** | Edge Application Server (EAS) | MEC Application | Unified: EAS = MEC App |
| **Discovery** | EES API (EDGE-1) | MEC Catalog | Dual: EES for 5G, Catalog for MEC |
| **Lifecycle** | EES + ECS | MEO + MEPM | Converged lifecycle mgmt |
| **Service Continuity** | ACT (App Context Transfer) | App relocation | Implement both mechanisms |
| **Standardization** | 3GPP (TS 23.558) | ETSI (GS MEC 003, 010-2) | Comply with both |
| **Certification** | Conformance testing | MECwiki registration | Dual certification path |

**Recommendation:** Implement **both** standards for maximum market reach:
- **ETSI MEC** for general edge orchestration, operator-agnostic deployments
- **3GPP EDGEAPP** for 5G-native integration, tight coupling with 5G Core
- **Converged Architecture**: Single Nuvla platform with dual interfaces (MEO + EES/ECS)

---

## 10. Conclusion

Achieving 3GPP edge computing compliance requires **37-49 weeks** of development (Phase 1 + 2) to implement EES, ECS, and 5G Core integration (NEF, AF).

**Critical Path:** EES Implementation → ECS Implementation → NEF Integration → AF Integration → Service Continuity

**Key Challenges:**
1. **5G Core Complexity**: NEF, AF integration requires deep 5G knowledge, access to 5GC
2. **Application Context Transfer**: Stateful handover is complex, requires coordination
3. **Security**: OAuth/OIDC, mTLS, API security add significant effort
4. **Dual Standards**: Supporting both ETSI MEC and 3GPP increases scope

**Recommendation:** Execute **Phase 1 + 2 together** (37-49 weeks total) for production-ready 3GPP edge platform suitable for 5G-EMERGE deployment and operator validation.

**Strategic Positioning:**
- **Short-term (Phase 1)**: Focus on ETSI MEC compliance for MECwiki registration (10-14 weeks)
- **Medium-term (Phase 2)**: Add 3GPP EDGEAPP support for 5G integration (37-49 weeks total)
- **Long-term (Phase 3)**: Converged MEC+3GPP platform with advanced features (12-18 months)

**Next Steps:**
1. **Secure 2-3 senior developers** with 5G + edge expertise
2. **Phase 1 kickoff**: EES/ECS architecture design (weeks 1-2)
3. **5G Core setup**: Deploy Open5GS/free5GC simulator (week 2)
4. **EES implementation**: Discovery, provisioning APIs (weeks 3-10)
5. **ECS implementation**: Configuration, ACT APIs (weeks 11-15)
6. **NEF integration**: Event subscriptions, analytics (weeks 16-22)
7. **AF integration**: Policy, QoS coordination (weeks 23-28)
8. **Phase 2 execution**: Lifecycle, service continuity, analytics, security (weeks 29-49)
9. **Validation & testing**: Conformance testing, operator trials (weeks 50-54)
10. **Production deployment**: 5G-EMERGE integration (week 55+)

---

## Appendix A: 3GPP Specifications Reference

**Core Specifications:**
- **TS 23.558** v19.6.0: Architecture for enabling Edge Applications (EDGEAPP)
- **TS 23.501** v19.5.0: System architecture for the 5G System (5GS)
- **TS 23.502** v19.5.0: Procedures for the 5G System (5GS)
- **TS 28.538** v17.4.0: Management and orchestration of edge computing
- **TS 29.522** v19.3.0: Network Exposure Function (NEF) Northbound APIs
- **TS 29.514** v19.5.0: Policy and Charging Control (Npcf) APIs
- **TS 33.558** v17.5.0: Security aspects of edge computing

**Supporting Specifications:**
- **TS 23.503**: Policy and charging control framework for 5GS
- **TS 23.288**: Architecture enhancements for network data analytics (NWDAF)
- **TS 29.122**: T8 interface (SCEF) - precursor to NEF
- **TS 29.551**: Nnef_ServiceDiscovery API
- **TS 34.229**: Conformance testing for edge applications

---

## Appendix B: Existing Nuvla Capabilities Mapping

| Nuvla Component | 3GPP Equivalent | Adaptation Required |
|-----------------|-----------------|---------------------|
| **Module Resource** | EdgeAppInfo | Add service area, EAS endpoints, 3GPP metadata |
| **Deployment Resource** | EAS Instance | Add ACT support, 5G QoS policies, state mgmt |
| **NuvlaEdge Resource** | Edge Compute Node | Add EAS registry, 5G site info, UPF proximity |
| **Job Resource** | Operation Tracking | Map to ACT operations, NEF event handling |
| **Event Resource** | NEF Notifications | Integrate NEF webhooks, UE mobility events |
| **ACL Resource** | OAuth/OIDC | Extend with OAuth 2.0, JWT tokens |
| **Credential Resource** | API Keys/Tokens | Add NEF credentials, AF authentication |
| **Infrastructure Service** | EES/ECS | Implement new EES/ECS resources with APIs |

**Strategy:** Extend Nuvla resources with 3GPP-specific fields and logic, implement EES/ECS as new resource types.

---

## Appendix C: 5G Core Simulator Options

For development and testing without live 5G network:

1. **Open5GS** (Open Source)
   - Full 5G Core implementation (AMF, SMF, UPF, PCF, NEF, etc.)
   - Best for: Complete 5G Core testing
   - Complexity: High (requires networking knowledge)
   - Cost: Free

2. **free5GC** (Open Source)
   - Lightweight 5G Core in Go
   - Best for: Quick setup, NEF/AF testing
   - Complexity: Medium
   - Cost: Free

3. **UERANSIM** (Open Source)
   - 5G UE and RAN simulator
   - Best for: Testing UE mobility, handover scenarios
   - Complexity: Low
   - Cost: Free

4. **Amarisoft** (Commercial)
   - Professional 5G Core + RAN simulator
   - Best for: Production-grade testing, operator trials
   - Complexity: Low (turnkey)
   - Cost: €10,000 - €50,000/year

**Recommendation:** Start with **Open5GS + UERANSIM** for Phase 1 development, upgrade to Amarisoft for operator validation in Phase 2.

---

**Document Status:** Final  
**Owner:** Nuvla Engineering / 5G-EMERGE Project  
**Revision History:**
- v1.0 (2025-10-24): Initial compliance study
