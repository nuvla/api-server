# MEC 003 Implementation Plan
## Framework and Reference Architecture Compliance

**Document Version:** 1.0  
**Date:** 21 October 2025  
**Project:** 5G-EMERGE / Nuvla.io  
**Target Standard:** ETSI GS MEC 003 v3.1.1

---

## Executive Summary

This document outlines a detailed implementation plan to align Nuvla.io with the **ETSI MEC 003 Framework and Reference Architecture**. MEC 003 is the foundational specification that defines the overall MEC system architecture, functional blocks, reference points, and deployment models.

**Why MEC 003 Matters:**
- 🏗️ **Foundation:** Defines the architectural blueprint for all other MEC standards
- 🔌 **Reference Points:** Specifies interfaces (Mp1, Mp2, Mp3, Mm1-Mm9) between components
- 📦 **Deployment Models:** Describes how MEC systems are structured and deployed
- 🔒 **Security Framework:** Establishes trust domains and security requirements

**Current Status:** ~40% architectural alignment (edge infrastructure exists, MEC abstractions missing)  
**Target:** 85%+ architectural compliance  
**Estimated Effort:** 22 weeks (880 hours)  
**Team Size:** 3-4 developers + 1 DevOps + 0.5 FTE architect + 0.5 FTE QA  
**Investment:** €240k - €300k

**Note:** MEC 003 is primarily an **architectural specification**, not an API specification. Implementation focuses on system design alignment, component mapping, and creating the necessary abstractions to support other MEC standards.

---

## 1. MEC 003 Architecture Overview

### 1.1 Core Architectural Components

```
┌─────────────────────────────────────────────────────────────────┐
│                     OSS (Operations Support System)             │
│                   CFS Portal (Customer Facing Service)          │
└────────────────────────────┬────────────────────────────────────┘
                             │ Mm3
                ┌────────────▼────────────┐
                │   MEC System Level      │
                │   Management (MEO)      │  <-- MEC Orchestrator
                │                         │
                │  - App Lifecycle Mgmt   │
                │  - Service Orchestration│
                │  - Multi-host Mgmt      │
                └────┬──────────────┬─────┘
                Mm5  │              │ Mm2
        ┌────────────▼───┐    ┌────▼──────────────┐
        │ MEC Platform    │    │ Virtualization    │
        │ Manager (MEPM)  │    │ Infrastructure    │
        │                 │    │ Manager (VIM)     │
        └────┬────────────┘    └───────────────────┘
        Mm7  │
    ┌────────▼─────────────────────────────────────────────┐
    │              MEC Host (Edge Node)                    │
    │  ┌────────────────────────────────────────┐         │
    │  │      MEC Platform (MEP)                │         │
    │  │  ┌──────────────────────────────────┐  │         │
    │  │  │  MEC Platform Services           │  │         │
    │  │  │  - Service Registry              │  │         │
    │  │  │  - Traffic Rules Control         │  │         │
    │  │  │  - DNS Handling                  │  │         │
    │  │  └──────────────────────────────────┘  │         │
    │  │                                         │         │
    │  │  Mp1 (Application Enablement)          │         │
    │  │  ↓                                      │         │
    │  │  ┌──────────────────────────────────┐  │         │
    │  │  │  MEC Applications                │  │         │
    │  │  │  - App #1, App #2, App #3        │  │         │
    │  │  └──────────────────────────────────┘  │         │
    │  └─────────────────────────────────────────┘         │
    │                                                       │
    │  ┌─────────────────────────────────────────┐         │
    │  │  Virtualization Infrastructure          │         │
    │  │  (Compute, Storage, Network)            │         │
    │  └─────────────────────────────────────────┘         │
    └───────────────────────────────────────────────────────┘
```

### 1.2 Key MEC 003 Components

| Component | Abbreviation | Role |
|-----------|--------------|------|
| **MEC Orchestrator** | MEO | System-level lifecycle management and service orchestration |
| **MEC Platform Manager** | MEPM | Platform-level configuration and lifecycle management |
| **MEC Platform** | MEP | Runtime environment providing platform services to apps |
| **MEC Host** | - | Physical or virtual hosting environment |
| **Virtualization Infrastructure Manager** | VIM | Manages compute, storage, network resources |
| **MEC Application** | ME App | Application running on MEC platform |
| **Operations Support System** | OSS | Operator systems for management |
| **User Application LCM Proxy** | UALCMP | Proxies user app requests to MEO |

### 1.3 Reference Points (Interfaces)

| Reference Point | Between | Purpose |
|----------------|---------|---------|
| **Mp1** | ME App ↔ MEP | Application enablement, service discovery |
| **Mp2** | MEP ↔ Data Plane | User plane traffic |
| **Mp3** | ME App ↔ ME App | Inter-application communication |
| **Mm1** | MEO ↔ OSS | System management |
| **Mm2** | MEO ↔ VIM | Resource management |
| **Mm3** | MEO ↔ CFS Portal | Customer-facing services |
| **Mm4** | MEO ↔ UALCMP | User app lifecycle proxy |
| **Mm5** | MEO ↔ MEPM | Platform management |
| **Mm6** | MEPM ↔ MEP | Platform configuration |
| **Mm7** | MEPM ↔ VIM | Platform-level resource management |
| **Mm8** | MEO ↔ MEO | Multi-MEO federation |
| **Mm9** | MEO ↔ ME App | Direct app management (optional) |

---

## 2. Current State Assessment

### 2.1 Mapping Nuvla to MEC Components

| MEC Component | Nuvla Equivalent | Coverage | Gap |
|---------------|------------------|----------|-----|
| **MEO (Orchestrator)** | Nuvla Server Core | 70% | Missing multi-host orchestration, federation |
| **MEPM (Platform Manager)** | NuvlaBox Agent | 60% | Missing MEC-specific platform services |
| **MEP (Platform)** | NuvlaBox Runtime | 50% | Missing Mp1 interface, service registry |
| **MEC Host** | NuvlaBox Hardware | 80% | Good mapping, missing MEC metadata |
| **VIM** | K8s/Docker | 70% | Via infrastructure-service, indirect |
| **ME App** | Deployment | 60% | Missing MEC app descriptor format |
| **OSS** | External (Customer) | 0% | No standardized integration |
| **CFS Portal** | Nuvla UI | 40% | Missing MEC-specific workflows |

### 2.2 Strengths

✅ **Strong Edge Infrastructure**
- NuvlaBox provides robust edge device management
- Multi-cloud deployment capabilities
- Container orchestration (K8s, Docker)
- Real-time telemetry and monitoring

✅ **Application Lifecycle Management**
- Deployment resource handles instantiation
- Job resource tracks operations
- Event system for notifications
- Multi-tenancy and RBAC

✅ **Existing Reference Points**
- REST API (similar to Mm5, Mp1)
- Kafka event bus (internal messaging)
- WebSocket for real-time updates

### 2.3 Critical Gaps

🔴 **Missing MEC Platform Services**
- No service registry for MEC services
- No traffic rules engine
- No DNS rule configuration
- No bandwidth management

🔴 **No Mp1 Interface**
- Applications can't discover MEC services
- No standardized application enablement API
- Missing service consumption model

🔴 **Limited Multi-Host Orchestration**
- Deployments target single NuvlaBox or cluster
- No cross-edge coordination
- Missing application placement optimization

🔴 **No Federation Support (Mm8)**
- Single Nuvla instance only
- No inter-platform communication
- Missing trust models for federation

---

## 3. Implementation Phases

### Phase 1: Architectural Foundation (Weeks 1-8)
**Goal:** Establish MEC-compliant architectural components and abstractions

#### 3.1 MEC Component Abstraction Layer (Week 1-3)

**Tasks:**
1. Create MEC component resource definitions
   ```clojure
   ;; New namespace: com.sixsq.nuvla.server.resources.mec
   
   ;; MEO (MEC Orchestrator) - System-level management
   (ns com.sixsq.nuvla.server.resources.mec.orchestrator
     "MEC Orchestrator abstraction - maps to Nuvla core server")
   
   ;; MEPM (MEC Platform Manager) - Platform-level management
   (ns com.sixsq.nuvla.server.resources.mec.platform-manager
     "MEC Platform Manager - maps to NuvlaBox management")
   
   ;; MEP (MEC Platform) - Runtime platform services
   (ns com.sixsq.nuvla.server.resources.mec.platform
     "MEC Platform services - new abstraction")
   
   ;; MEC Host metadata
   (ns com.sixsq.nuvla.server.resources.mec.host
     "MEC Host representation - extends NuvlaBox")
   ```

2. Define MEC system configuration
   ```clojure
   ;; resources/mec-config.edn
   {:mec-system
    {:name "Nuvla MEC System"
     :version "3.1.1"  ;; MEC 003 version
     :deployment-model :distributed
     :federation-enabled false  ;; Phase 3 feature
     :components
     {:meo {:enabled true
            :endpoint "https://nuvla.io/api"}
      :mepm {:multi-host true
             :nuvlabox-integration true}
      :mep {:service-registry true
            :traffic-rules false     ;; Phase 2
            :dns-rules false}}}     ;; Phase 2
    
    :reference-points
    {:mp1 {:enabled false}  ;; Phase 2
     :mm5 {:enabled true}   ;; REST API
     :mm8 {:enabled false}}}  ;; Phase 3 federation
   ```

3. Create MEC metadata schema
   ```clojure
   ;; Extend NuvlaBox with MEC Host metadata
   (def mec-host-attrs
     {:mec-capabilities {:type "map"
                         :description "MEC host capabilities"}
      :serving-area     {:type "map"
                         :description "Geographic serving area"}
      :mep-info         {:type "map"
                         :description "MEP platform information"}
      :available-services {:type "array"
                           :description "List of available MEC services"}})
   
   ;; Extend deployment with MEC App metadata
   (def mec-app-attrs
     {:app-d-id         {:type "string"
                         :description "Application Descriptor ID"}
      :mec-version      {:type "string"
                         :description "MEC API version"}
      :required-services {:type "array"
                          :description "Required MEC services"}
      :traffic-rules    {:type "array"
                         :description "Traffic rule requirements"}
      :dns-rules        {:type "array"
                         :description "DNS rule requirements"}})
   ```

4. Implement component lifecycle
   - MEO initialization on server startup
   - MEPM registration for each NuvlaBox
   - MEP capability advertisement
   - Component health monitoring

**Deliverables:**
- ✅ MEC component abstractions (4 namespaces)
- ✅ System configuration schema
- ✅ Extended resource metadata
- ✅ Component registration & discovery
- ✅ Unit tests

**Effort:** 180 hours (3 devs × 2 weeks)

---

#### 3.2 Reference Point Implementation (Week 4-6)

**Tasks:**
1. **Mm5 Interface (MEO ↔ MEPM)**
   - Already partially exists via REST API
   - Add MEC-specific endpoints
   ```
   GET  /api/mec/platform-managers           # List MEPMs
   GET  /api/mec/platform-managers/{id}      # Get MEPM details
   POST /api/mec/platform-managers/{id}/configure
   ```

2. **Mm6 Interface (MEPM ↔ MEP)**
   - NuvlaBox agent communication
   - Add platform configuration endpoints
   ```json
   {
     "operation": "configure-mep",
     "config": {
       "service-registry": true,
       "available-services": ["rnis", "location"],
       "resource-limits": {...}
     }
   }
   ```

3. **Mm7 Interface (MEPM ↔ VIM)**
   - Map to existing infrastructure-service
   - Add resource query capabilities
   ```clojure
   (defn query-vim-resources
     [mepm-id]
     {:compute {:cpu {:total 32 :available 16}
                :memory {:total "64GB" :available "32GB"}}
      :storage {:total "1TB" :available "500GB"}
      :network {:interfaces ["eth0" "eth1"]
                :bandwidth "10Gbps"}})
   ```

4. **Mm2 Interface (MEO ↔ VIM)**
   - System-level resource management
   - Multi-host resource aggregation
   ```
   GET  /api/mec/resources              # Aggregate view
   POST /api/mec/resources/reserve      # Reserve resources
   ```

5. Create reference point documentation
   - API specifications for each interface
   - Sequence diagrams for common flows
   - Error handling and retries

**Deliverables:**
- ✅ Mm5 MEC-specific endpoints
- ✅ Mm6 platform configuration protocol
- ✅ Mm7 resource management integration
- ✅ Mm2 system-level resource API
- ✅ Reference point documentation
- ✅ Integration tests

**Effort:** 180 hours (3 devs × 2 weeks)

---

#### 3.3 MEC Service Registry (Week 7-8)

**Tasks:**
1. Design service registry data model
   ```clojure
   (ns com.sixsq.nuvla.server.resources.mec-service
     "MEC Service registration and discovery")
   
   (def resource-type "mec-service")
   
   (def resource-attrs
     {:ser-name         {:type "string"
                         :required true
                         :description "Service name (e.g., rnis, location)"}
      :ser-category     {:type "map"
                         :description "Service category per MEC 011"}
      :version          {:type "string"
                         :description "Service API version"}
      :state            {:type "string"
                         :enum ["ACTIVE" "INACTIVE" "SUSPENDED"]}
      :transport-info   {:type "map"
                         :description "Endpoint and protocol info"}
      :serializer       {:type "string"
                         :enum ["JSON" "XML" "PROTOBUF"]}
      :scope-of-locality {:type "string"
                          :enum ["MEC_SYSTEM" "MEC_HOST" "NFVI_POP"]}
      :consumed-services {:type "array"
                          :description "Services this service depends on"}
      :is-local         {:type "boolean"
                         :description "Local vs remote service"}
      :liveness-interval {:type "integer"
                          :description "Health check interval (seconds)"}})
   ```

2. Implement service registration API
   ```
   POST   /api/mec/services              # Register service
   GET    /api/mec/services              # List services
   GET    /api/mec/services/{id}         # Get service details
   PUT    /api/mec/services/{id}         # Update service
   DELETE /api/mec/services/{id}         # Deregister service
   GET    /api/mec/services?category=rnis&state=ACTIVE
   ```

3. Build service discovery mechanism
   ```clojure
   (defn discover-services
     [query-params]
     ;; Query parameters:
     ;; - ser-name: Service name filter
     ;; - ser-category: Category filter
     ;; - scope-of-locality: Scope filter
     ;; - is-local: Local only flag
     (filter-services
       (get-all-services)
       query-params))
   ```

4. Add service health monitoring
   - Periodic liveness checks
   - Automatic service deregistration on failure
   - Health status updates

5. Integrate with NuvlaBox
   - NuvlaBox reports available local services
   - Services advertised to MEP
   - Dynamic service availability updates

**Deliverables:**
- ✅ MEC service resource definition
- ✅ Service registration API (5 endpoints)
- ✅ Service discovery logic
- ✅ Health monitoring system
- ✅ NuvlaBox integration
- ✅ Unit & integration tests

**Effort:** 120 hours (2 devs × 2 weeks)

---

### Phase 2: Platform Services & Mp1 Interface (Weeks 9-16)
**Goal:** Implement MEC Platform services and application enablement

#### 3.4 Mp1 Application Enablement Interface (Week 9-11)

**Tasks:**
1. Create Mp1 API specification
   ```
   # Service discovery
   GET  /mp1/v2/services                    # List available services
   GET  /mp1/v2/services/{serviceId}        # Get service details
   
   # Service availability subscription
   POST   /mp1/v2/applications/{appInstanceId}/subscriptions
   GET    /mp1/v2/applications/{appInstanceId}/subscriptions
   DELETE /mp1/v2/applications/{appInstanceId}/subscriptions/{subscriptionId}
   
   # DNS rules (per MEC 011)
   GET  /mp1/v2/applications/{appInstanceId}/dns_rules
   POST /mp1/v2/applications/{appInstanceId}/dns_rules
   PUT  /mp1/v2/applications/{appInstanceId}/dns_rules/{dnsRuleId}
   
   # Traffic rules (per MEC 011)
   GET  /mp1/v2/applications/{appInstanceId}/traffic_rules
   POST /mp1/v2/applications/{appInstanceId}/traffic_rules
   PUT  /mp1/v2/applications/{appInstanceId}/traffic_rules/{trafficRuleId}
   
   # Application information
   GET  /mp1/v2/applications/{appInstanceId}
   PUT  /mp1/v2/applications/{appInstanceId}
   ```

2. Implement Mp1 in NuvlaBox agent
   - Expose Mp1 endpoint on each NuvlaBox
   - Local service discovery
   - Application authentication
   ```python
   # NuvlaBox provides Mp1 endpoint
   # http://nuvlabox-ip:8080/mp1/v2/
   
   class Mp1Server:
       def list_services(self, filters):
           """List MEC services available on this host"""
           return self.service_registry.query(filters)
       
       def subscribe_service_availability(self, app_id, subscription):
           """Subscribe to service availability notifications"""
           return self.subscription_manager.create(app_id, subscription)
   ```

3. Create Mp1 client SDK
   ```python
   # Example: Python SDK for MEC apps
   from nuvla_mec import Mp1Client
   
   # Application uses this to discover services
   client = Mp1Client(mp1_endpoint="http://mep:8080/mp1/v2")
   
   # Discover RNIS service
   rnis_services = client.discover_services(
       ser_category={"href": "/mec_service_mgmt/v1/rnis", "id": "id"},
       is_local=True
   )
   
   # Subscribe to service availability
   subscription = client.subscribe_service_availability(
       app_instance_id="deployment-123",
       callback_uri="http://my-app/notifications",
       service_names=["rnis"]
   )
   ```

4. Add authentication for Mp1
   - Application authentication tokens
   - ACL enforcement
   - Rate limiting

**Deliverables:**
- ✅ Mp1 API specification (OpenAPI)
- ✅ Mp1 server in NuvlaBox agent
- ✅ Mp1 client SDK (Python, JavaScript)
- ✅ Authentication & authorization
- ✅ Integration tests

**Effort:** 180 hours (3 devs × 2 weeks)

---

#### 3.5 Traffic Rules Engine (Week 12-13)

**Tasks:**
1. Design traffic rule data model
   ```clojure
   (def traffic-rule-attrs
     {:traffic-rule-id   {:type "string"}
      :filter-type       {:type "string"
                          :enum ["FLOW" "PACKET"]}
      :priority          {:type "integer"
                          :min 0 :max 255}
      :traffic-filter    {:type "array"
                          :description "5-tuple filters"}
      :action            {:type "string"
                          :enum ["DROP" "FORWARD" "PASSTHROUGH" 
                                 "DUPLICATE_DECAPSULATE" "DUPLICATE"]}
      :dst-interface     {:type "array"
                          :description "Destination interfaces"}
      :state             {:type "string"
                          :enum ["ACTIVE" "INACTIVE"]}})
   ```

2. Implement traffic rule API
   ```
   GET    /api/mec/traffic-rules
   POST   /api/mec/traffic-rules
   PUT    /api/mec/traffic-rules/{id}
   DELETE /api/mec/traffic-rules/{id}
   ```

3. Create traffic rule enforcement
   - Integration with iptables/nftables
   - Network policy generation for K8s
   - Traffic steering configuration
   ```clojure
   (defn apply-traffic-rule
     [rule]
     (case (:action rule)
       "FORWARD"    (create-forward-rule rule)
       "DROP"       (create-drop-rule rule)
       "DUPLICATE"  (create-duplicate-rule rule)))
   ```

4. NuvlaBox traffic rule agent
   - Listen for traffic rule updates
   - Apply rules to local networking
   - Report rule status

**Deliverables:**
- ✅ Traffic rule resource
- ✅ Traffic rule API
- ✅ Rule enforcement engine
- ✅ NuvlaBox integration
- ✅ Integration tests

**Effort:** 120 hours (2 devs + 1 DevOps × 1.5 weeks)

---

#### 3.6 DNS Rules Engine (Week 14-15)

**Tasks:**
1. Design DNS rule data model
   ```clojure
   (def dns-rule-attrs
     {:dns-rule-id       {:type "string"}
      :domain-name       {:type "string"
                          :description "FQDN to match"}
      :ip-address-type   {:type "string"
                          :enum ["IP_V4" "IP_V6"]}
      :ip-address        {:type "string"
                          :description "IP address to return"}
      :ttl               {:type "integer"
                          :description "DNS TTL in seconds"}
      :state             {:type "string"
                          :enum ["ACTIVE" "INACTIVE"]}})
   ```

2. Implement DNS rule API
   ```
   GET    /api/mec/dns-rules
   POST   /api/mec/dns-rules
   PUT    /api/mec/dns-rules/{id}
   DELETE /api/mec/dns-rules/{id}
   ```

3. DNS rule enforcement
   - Integration with CoreDNS/dnsmasq
   - Dynamic DNS record updates
   - Split-horizon DNS configuration
   ```yaml
   # CoreDNS configuration
   .:53 {
       forward . /etc/resolv.conf
       cache 30
       reload
       
       # MEC DNS rules plugin
       mec_dns_rules {
           endpoint http://nuvla-api/mec/dns-rules
           refresh 10s
       }
   }
   ```

4. NuvlaBox DNS integration
   - Local DNS server with MEC rule support
   - DNS rule synchronization
   - Health monitoring

**Deliverables:**
- ✅ DNS rule resource
- ✅ DNS rule API
- ✅ DNS server integration
- ✅ CoreDNS plugin (or dnsmasq config)
- ✅ NuvlaBox DNS agent
- ✅ Integration tests

**Effort:** 100 hours (2 devs + 1 DevOps × 1.5 weeks)

---

#### 3.7 Multi-Host Orchestration (Week 16)

**Tasks:**
1. Application placement optimization
   ```clojure
   (defn select-optimal-host
     [app-requirements available-hosts]
     ;; Placement criteria:
     ;; - Resource availability (CPU, memory, storage)
     ;; - Network latency (proximity to users)
     ;; - Service availability (required MEC services)
     ;; - Load balancing (distribute load)
     ;; - Affinity/anti-affinity rules
     (score-and-rank-hosts app-requirements available-hosts))
   ```

2. Cross-host coordination
   - Application dependencies across hosts
   - Service mesh integration
   - Load balancing

3. Multi-host monitoring
   - Aggregate resource view
   - Cross-host application tracking
   - System-level dashboards

**Deliverables:**
- ✅ Placement algorithm
- ✅ Multi-host orchestration logic
- ✅ System-level monitoring
- ✅ Integration tests

**Effort:** 60 hours (2 devs × 1 week)

---

### Phase 3: Advanced Features & Federation (Weeks 17-22)
**Goal:** Implement advanced MEC capabilities and federation

#### 3.8 Trust Domains & Security (Week 17-18)

**Tasks:**
1. Define MEC trust domains
   ```clojure
   (def trust-domains
     {:operator-domain    {:description "Mobile operator infrastructure"
                           :trust-level :high}
      :third-party-domain {:description "3rd party MEC applications"
                           :trust-level :medium}
      :user-domain        {:description "User-facing applications"
                           :trust-level :medium}
      :external-domain    {:description "External services"
                           :trust-level :low}})
   ```

2. Implement trust boundaries
   - Network isolation per trust domain
   - Access control policies
   - API authentication per domain
   - Certificate management

3. Security monitoring
   - Audit logging
   - Intrusion detection
   - Compliance reporting

**Deliverables:**
- ✅ Trust domain model
- ✅ Security policies
- ✅ Network isolation
- ✅ Audit logging
- ✅ Security tests

**Effort:** 120 hours (2 devs + 1 security specialist × 2 weeks)

---

#### 3.9 Federation Support - Mm8 Interface (Week 19-21)

**Tasks:**
1. Design federation architecture
   ```
   ┌──────────────┐         Mm8          ┌──────────────┐
   │  Nuvla MEO   │◄──────────────────►  │  Remote MEO  │
   │  Instance A  │     Federation       │  Instance B  │
   └──────────────┘                      └──────────────┘
         │                                      │
         ├─ MEC Hosts (Region A)                ├─ MEC Hosts (Region B)
         └─ Applications                        └─ Applications
   ```

2. Implement Mm8 interface
   ```
   # Federation discovery
   GET  /api/mec/federation/peers          # List federated MEOs
   POST /api/mec/federation/peers          # Register peer MEO
   
   # Cross-MEO operations
   POST /api/mec/federation/app-instances  # Deploy to remote MEO
   GET  /api/mec/federation/resources      # Query remote resources
   
   # Service exposure
   GET  /api/mec/federation/services       # Discover remote services
   ```

3. Federation trust model
   - Mutual TLS authentication
   - Token exchange
   - Authorization policies for cross-platform access

4. Cross-platform application deployment
   - Deploy app to remote MEC system
   - Application state synchronization
   - Federated monitoring

**Deliverables:**
- ✅ Federation architecture
- ✅ Mm8 interface implementation
- ✅ Trust model for federation
- ✅ Cross-platform deployment
- ✅ Federation tests

**Effort:** 180 hours (3 devs + 0.5 architect × 2 weeks)

---

#### 3.10 Integration & Compliance Testing (Week 22)

**Tasks:**
1. MEC 003 compliance validation
   - Verify all components implemented
   - Check reference point coverage
   - Validate deployment models

2. End-to-end testing
   - Multi-host deployment scenarios
   - Service discovery workflows
   - Traffic/DNS rule enforcement
   - Federation scenarios

3. Performance testing
   - Load testing (100+ hosts, 1000+ apps)
   - Service discovery latency
   - Rule enforcement overhead

4. Documentation
   - MEC 003 compliance report
   - Architecture diagrams
   - Deployment guides

**Deliverables:**
- ✅ Compliance test suite
- ✅ Test results report
- ✅ Performance benchmarks
- ✅ Architecture documentation

**Effort:** 80 hours (2 devs + 1 QA × 1.5 weeks)

---

## 4. Technical Architecture

### 4.1 Layered Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Layer 5: Federation                     │
│  • Mm8 Interface (MEO ↔ MEO)                               │
│  • Cross-platform orchestration                             │
│  • Federated service discovery                              │
└─────────────────────────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────┐
│               Layer 4: MEC System Management                │
│  • MEO (Nuvla Core Server)                                 │
│  • Multi-host orchestration                                 │
│  • System-level resource management (Mm2)                   │
│  • Application placement & scheduling                       │
└─────────────────────────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────┐
│             Layer 3: MEC Platform Management                │
│  • MEPM (NuvlaBox Management API)                          │
│  • Mm5 Interface (MEO ↔ MEPM)                              │
│  • Mm7 Interface (MEPM ↔ VIM)                              │
│  • Platform configuration & monitoring                      │
└─────────────────────────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────┐
│              Layer 2: MEC Platform Services                 │
│  • MEP (Platform Runtime)                                   │
│  • Service Registry                                         │
│  • Traffic Rules Engine                                     │
│  • DNS Rules Engine                                         │
│  • Mp1 Interface (App Enablement)                          │
└─────────────────────────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────┐
│            Layer 1: Virtualization Infrastructure           │
│  • Kubernetes / Docker (Container orchestration)            │
│  • Compute, Storage, Network resources                      │
│  • Infrastructure-level monitoring                          │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 Data Flow: Application Deployment with MEC Services

```
1. User → POST /api/deployment (with MEC app descriptor)
          {
            "module": "app-abc",
            "mec-config": {
              "required-services": ["rnis"],
              "traffic-rules": [...],
              "dns-rules": [...]
            }
          }

2. MEO (Nuvla Core)
   ├─ Parse MEC app descriptor
   ├─ Query available hosts via MEPM (Mm5)
   ├─ Run placement algorithm (select optimal host)
   └─ Initiate deployment

3. MEPM (NuvlaBox Management)
   ├─ Receive deployment request (Mm5)
   ├─ Query VIM for resources (Mm7)
   ├─ Validate service availability on MEP
   └─ Approve deployment

4. MEP (Platform on NuvlaBox)
   ├─ Register app instance
   ├─ Provision Mp1 access credentials
   ├─ Configure traffic rules (if any)
   ├─ Configure DNS rules (if any)
   └─ Report readiness

5. VIM (K8s/Docker)
   ├─ Create containers
   ├─ Setup networking
   └─ Start application

6. ME App (Deployed Application)
   ├─ Authenticate with Mp1
   ├─ Discover required services (RNIS)
   ├─ Subscribe to service notifications
   └─ Start serving traffic

7. MEO ← Deployment complete
   └─ Update deployment state
```

---

## 5. Resource Estimates

### 5.1 Effort Breakdown

| Phase | Tasks | Dev Hours | DevOps Hours | Architect Hours | QA Hours | Total |
|-------|-------|-----------|--------------|----------------|----------|-------|
| **Phase 1** | Architectural Foundation | 360 | 60 | 60 | 0 | 480 |
| **Phase 2** | Platform Services & Mp1 | 320 | 80 | 0 | 40 | 440 |
| **Phase 3** | Advanced & Federation | 220 | 40 | 60 | 80 | 400 |
| **PM/Overhead** | Planning, reviews, coordination | - | - | - | - | 120 |
| **TOTAL** | | 900 | 180 | 120 | 120 | **1320** |

**Note:** Actual implementation is ~880 hours; ~440 hours overlap with MEC 010-2 work (shared infrastructure).

### 5.2 Team Structure

**Core Team:**
- 3x Senior Backend Developers (Clojure, distributed systems)
- 1x DevOps Engineer (Networking, K8s, security)
- 0.5 FTE Solutions Architect (MEC expertise, system design)
- 0.5 FTE QA Engineer (Testing, compliance validation)
- 0.2 FTE Project Manager

**Specialist Support:**
- 0.3 FTE Security Engineer (trust domains, federation security, weeks 17-18)
- 0.2 FTE Network Engineer (traffic rules, DNS, weeks 12-15)

### 5.3 Investment Estimate

**Personnel Costs:**
- Senior Developer: €800/day × 3 × 110 days = €264,000
- DevOps Engineer: €750/day × 1 × 110 days = €82,500
- Solutions Architect: €900/day × 0.5 × 110 days = €49,500
- QA Engineer: €600/day × 0.5 × 110 days = €33,000
- PM: €700/day × 0.2 × 110 days = €15,400
- Security Engineer: €850/day × 0.3 × 20 days = €5,100
- Network Engineer: €750/day × 0.2 × 20 days = €3,000

**Total Personnel:** €452,500

**With Resource Sharing (MEC 010-2 overlap, ~30% reduction):** €240k - €300k

---

## 6. Risk Assessment

### 6.1 Technical Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| **Mp1 complexity** | Medium | High | Iterative implementation, start simple |
| **Traffic rule enforcement** | High | Medium | Use proven tools (iptables, K8s policies) |
| **Federation security** | Medium | High | Adopt existing standards (OAuth2, mTLS) |
| **Performance overhead** | Medium | Medium | Caching, async processing, load testing |
| **Multi-host coordination** | Medium | High | Event-driven architecture, idempotency |

### 6.2 Organizational Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| **Scope creep (MEC services)** | High | High | Focus on framework first, services later |
| **Resource availability** | Medium | High | Secure commitments, clear priorities |
| **MEC spec interpretation** | Medium | Medium | Regular reviews with standards experts |
| **Integration with mobile networks** | Low | Medium | Use emulators, defer 3GPP integration |

---

## 7. Success Criteria

### 7.1 Functional Requirements

- ✅ All MEC 003 architectural components defined
- ✅ Key reference points implemented (Mm5, Mm6, Mm7, Mp1)
- ✅ Service registry operational
- ✅ Traffic and DNS rule engines functional
- ✅ Multi-host orchestration working
- ✅ Federation support (basic)

### 7.2 Non-Functional Requirements

- ✅ **Scalability:** Support 50+ MEC hosts, 500+ applications
- ✅ **Performance:** Service discovery <100ms, rule enforcement <50ms
- ✅ **Availability:** 99.9% platform uptime
- ✅ **Compliance:** 85%+ alignment with MEC 003 architecture
- ✅ **Security:** Trust domains enforced, audit logging active

### 7.3 Quality Gates

**Week 8 Checkpoint:**
- MEC component abstractions complete
- Service registry operational
- Reference points documented

**Week 16 Checkpoint:**
- Mp1 interface working
- Traffic/DNS rules functional
- Multi-host orchestration tested

**Week 22 Final:**
- Federation support validated
- Compliance testing complete
- Documentation published
- Production-ready

---

## 8. Dependencies & Prerequisites

### 8.1 Technical Dependencies

- ✅ Nuvla API server (Clojure 1.12)
- ✅ NuvlaBox agent (Python)
- ✅ Kubernetes/Docker orchestration
- ⚠️ Network policy support (K8s CNI, iptables/nftables)
- ⚠️ DNS server (CoreDNS or dnsmasq)
- ⚠️ Service mesh (optional, for advanced networking)

### 8.2 Knowledge Requirements

- Deep understanding of MEC 003 specification
- Network engineering (DNS, traffic routing, policies)
- Distributed systems architecture
- Security (TLS, OAuth2, trust models)
- Container orchestration (K8s)

### 8.3 External Dependencies

- ETSI MEC 003 v3.1.1 specification
- MEC 010-2, MEC 011 specifications (referenced)
- Test infrastructure with multiple hosts
- Optional: Access to mobile network testbed

---

## 9. Integration with Other MEC Standards

### 9.1 Foundation for Other Standards

MEC 003 provides the **architectural foundation** that enables:

| MEC Standard | Dependency on MEC 003 |
|--------------|----------------------|
| **MEC 010-2** | Uses MEO, MEPM, reference points (Mm5, Mm7) |
| **MEC 011** | Requires Mp1 interface, service registry |
| **MEC 021** | Uses MEO for mobility, Mp1 for app coordination |
| **MEC 037** | Uses MEO for package management |
| **MEC 040** | Requires Mm8 federation interface |

### 9.2 Implementation Order

```
1. MEC 003 (Foundation)        ← THIS PLAN
   ↓
2. MEC 010-2 (Lifecycle)       ← Already planned
   ↓
3. MEC 011 (Application Enablement) ← Extends Mp1
   ↓
4. MEC 037 (Packaging)
   ↓
5. MEC 021 (Mobility) + MEC 040 (Federation)
```

**Recommendation:** Implement MEC 003 and MEC 010-2 **in parallel** where possible:
- MEC 003 provides the framework
- MEC 010-2 provides the APIs
- Shared effort: ~30% overlap (service registry, resource management)

---

## 10. Next Steps

### 10.1 Immediate Actions (This Week)

1. **Stakeholder Review**
   - Review plan with 5G-EMERGE project team
   - Confirm priorities and scope
   - Get approval for budget and timeline

2. **Team Assembly**
   - Assign developers and specialists
   - Schedule kickoff meeting
   - Set up communication channels

3. **Environment Prep**
   - Create feature branch: `feature/mec-003-architecture`
   - Set up development/test infrastructure
   - Access to MEC specifications

### 10.2 Week 1 Kickoff

**Day 1-2: Planning**
- Team onboarding
- MEC 003 specification review
- Sprint planning (Week 1-2)

**Day 3-5: Implementation Start**
- Create MEC namespace structure
- Define component abstractions
- Set up configuration schema

### 10.3 Stakeholder Communication

- **Weekly:** Technical status report
- **Bi-weekly:** Demo of new capabilities
- **Monthly:** Compliance progress review
- **Quarterly:** Architecture review with MEC experts

---

## 11. Appendices

### Appendix A: MEC 003 Component Checklist

| Component | Nuvla Mapping | Status | Implementation Phase |
|-----------|---------------|--------|---------------------|
| MEO | Nuvla Core Server | ✅ Partial | Phase 1 |
| MEPM | NuvlaBox Management | ✅ Partial | Phase 1 |
| MEP | NuvlaBox Platform | ⚠️ New | Phase 2 |
| VIM | Infrastructure Service | ✅ Existing | - |
| MEC Host | NuvlaBox | ✅ Existing | - |
| ME App | Deployment | ✅ Partial | Phase 1 |
| Service Registry | - | ❌ Missing | Phase 1 |
| Mp1 Interface | - | ❌ Missing | Phase 2 |
| Traffic Rules | - | ❌ Missing | Phase 2 |
| DNS Rules | - | ❌ Missing | Phase 2 |
| Federation (Mm8) | - | ❌ Missing | Phase 3 |

### Appendix B: Reference Point Coverage

| Reference Point | Between | Implementation | Status |
|----------------|---------|----------------|--------|
| Mp1 | ME App ↔ MEP | Week 9-11 | Planned |
| Mp2 | MEP ↔ Data Plane | N/A (infrastructure) | - |
| Mp3 | ME App ↔ ME App | Existing (REST, networking) | ✅ |
| Mm1 | MEO ↔ OSS | External | Out of scope |
| Mm2 | MEO ↔ VIM | Week 4-6 | Planned |
| Mm3 | MEO ↔ CFS Portal | Existing (Nuvla UI) | ✅ Partial |
| Mm4 | MEO ↔ UALCMP | N/A | Out of scope |
| Mm5 | MEO ↔ MEPM | Week 4-6 | Planned |
| Mm6 | MEPM ↔ MEP | Week 4-6 | Planned |
| Mm7 | MEPM ↔ VIM | Week 4-6 | Planned |
| Mm8 | MEO ↔ MEO | Week 19-21 | Planned |
| Mm9 | MEO ↔ ME App | Optional | Out of scope |

### Appendix C: Useful Resources

- **ETSI MEC 003:** https://www.etsi.org/deliver/etsi_gs/MEC/001_099/003/
- **MEC Wiki:** https://mecwiki.etsi.org/
- **ETSI MEC Sandbox:** https://try-mec.etsi.org/
- **Nuvla Docs:** https://docs.nuvla.io/

---

## Change Log

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 21 Oct 2025 | GitHub Copilot | Initial implementation plan |

---

**Document Status:** Ready for Review  
**Next Review Date:** Week 8 (Checkpoint 1)  
**Owner:** 5G-EMERGE Technical Lead / MEC Architect
