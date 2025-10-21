# MEC 003 Architecture Diagrams
## Visual Reference for Nuvla as MEO

**Document Version:** 1.0  
**Date:** 21 October 2025  
**Purpose:** Visual diagrams for MEC 003 architectural alignment

---

## 1. MEC System Architecture Overview

### 1.1 Standard MEC 003 Architecture

```mermaid
graph TB
    subgraph "System Layer"
        MEO[MEO<br/>MEC Orchestrator]
        OSS[OSS<br/>Operations Support]
        UALCMP[User App LCM Proxy<br/>Customer Portal]
    end
    
    subgraph "Host Layer"
        MEPM[MEPM<br/>Platform Manager]
        VIM[VIM<br/>Infrastructure Manager]
    end
    
    subgraph "Platform Layer"
        MEP[MEP<br/>MEC Platform]
        SR[Service Registry]
        TR[Traffic Rules]
        DNS[DNS Rules]
    end
    
    subgraph "Application Layer"
        APP1[MEC App 1]
        APP2[MEC App 2]
    end
    
    MEO -->|Mm5| MEPM
    MEO -->|Mm2| VIM
    MEO -->|Mm3| UALCMP
    MEPM -->|Mp2| MEP
    MEPM -->|Mp3| VIM
    MEP -->|Mp1| APP1
    MEP -->|Mp1| APP2
    MEP --> SR
    MEP --> TR
    MEP --> DNS
    
    style MEO fill:#4CAF50,stroke:#333,stroke-width:3px,color:#fff
    style MEPM fill:#2196F3,stroke:#333,stroke-width:2px,color:#fff
    style MEP fill:#FF9800,stroke:#333,stroke-width:2px,color:#fff
```

### 1.2 Nuvla as MEO Architecture

```mermaid
graph TB
    subgraph "System Layer (Nuvla MEO)"
        API[Nuvla API Server<br/>MEO - System Orchestrator]
        UI[Web UI<br/>Customer Portal]
        ES[(Elasticsearch<br/>Database)]
        KAFKA[(Kafka<br/>Events)]
        ZK[(ZooKeeper<br/>Jobs)]
    end
    
    subgraph "Host Layer (External/NuvlaBox)"
        MEPM1[External MEPM<br/>e.g., OpenNESS]
        MEPM2[NuvlaBox Agent<br/>Enhanced MEPM]
        VIM1[Infrastructure Service<br/>Kubernetes]
        VIM2[Infrastructure Service<br/>Docker]
    end
    
    subgraph "Platform Layer (Out of Scope)"
        MEP1[External MEP<br/>Optional]
    end
    
    subgraph "Edge Devices"
        NB1[NuvlaBox 1<br/>MEC Host]
        NB2[NuvlaBox 2<br/>MEC Host]
    end
    
    subgraph "Applications"
        APP1[Edge App 1]
        APP2[Edge App 2]
    end
    
    UI -->|REST API| API
    API <--> ES
    API <--> KAFKA
    API <--> ZK
    
    API -->|Mm5| MEPM1
    API -->|Mm5| MEPM2
    API -->|Mm2| VIM1
    API -->|Mm2| VIM2
    
    MEPM1 --> NB1
    MEPM2 --> NB2
    
    NB1 --> APP1
    NB2 --> APP2
    
    MEPM1 -.->|Optional| MEP1
    
    style API fill:#4CAF50,stroke:#333,stroke-width:3px,color:#fff
    style UI fill:#2196F3,stroke:#333,stroke-width:2px,color:#fff
    style NB1 fill:#FF9800,stroke:#333,stroke-width:2px,color:#fff
    style NB2 fill:#FF9800,stroke:#333,stroke-width:2px,color:#fff
    style MEP1 fill:#E0E0E0,stroke:#999,stroke-width:1px,stroke-dasharray: 5 5
```

---

## 2. Component Mapping Diagram

```mermaid
graph LR
    subgraph "MEC 003 Components"
        M1[MEO]
        M2[Application Package]
        M3[Application Instance]
        M4[MEC Host]
        M5[MEPM]
        M6[VIM]
        M7[User App LCM Proxy]
    end
    
    subgraph "Nuvla Components"
        N1[Nuvla API Server]
        N2[Module]
        N3[Deployment]
        N4[NuvlaBox]
        N5[External MEPM /<br/>Enhanced Agent]
        N6[Infrastructure Service]
        N7[REST API + UI]
    end
    
    M1 -.->|95% aligned| N1
    M2 -.->|95% aligned| N2
    M3 -.->|95% aligned| N3
    M4 -.->|90% aligned| N4
    M5 -.->|50% aligned<br/>New integration| N5
    M6 -.->|85% aligned| N6
    M7 -.->|95% aligned| N7
    
    style M1 fill:#E3F2FD,stroke:#333,stroke-width:2px
    style M2 fill:#E3F2FD,stroke:#333,stroke-width:2px
    style M3 fill:#E3F2FD,stroke:#333,stroke-width:2px
    style M4 fill:#E3F2FD,stroke:#333,stroke-width:2px
    style M5 fill:#FFEBEE,stroke:#333,stroke-width:2px
    style M6 fill:#E3F2FD,stroke:#333,stroke-width:2px
    style M7 fill:#E3F2FD,stroke:#333,stroke-width:2px
    
    style N1 fill:#C8E6C9,stroke:#333,stroke-width:2px
    style N2 fill:#C8E6C9,stroke:#333,stroke-width:2px
    style N3 fill:#C8E6C9,stroke:#333,stroke-width:2px
    style N4 fill:#C8E6C9,stroke:#333,stroke-width:2px
    style N5 fill:#FFCCBC,stroke:#333,stroke-width:2px
    style N6 fill:#C8E6C9,stroke:#333,stroke-width:2px
    style N7 fill:#C8E6C9,stroke:#333,stroke-width:2px
```

---

## 3. Deployment Models

### 3.1 Model 1: Nuvla MEO + External MEPM

```mermaid
graph TB
    subgraph "Cloud / Datacenter"
        MEO[Nuvla MEO<br/>System Orchestrator]
    end
    
    subgraph "Edge Location 1"
        MEPM1[OpenNESS MEPM]
        HOST1[Edge Host 1]
        APP1[App Instance 1]
    end
    
    subgraph "Edge Location 2"
        MEPM2[Vendor MEPM]
        HOST2[Edge Host 2]
        APP2[App Instance 2]
    end
    
    subgraph "Edge Location 3"
        MEPM3[NuvlaBox MEPM]
        HOST3[NuvlaBox]
        APP3[App Instance 3]
    end
    
    MEO -->|Mm5<br/>REST/HTTPS| MEPM1
    MEO -->|Mm5<br/>REST/HTTPS| MEPM2
    MEO -->|Mm5<br/>REST/HTTPS| MEPM3
    
    MEPM1 --> HOST1
    MEPM2 --> HOST2
    MEPM3 --> HOST3
    
    HOST1 --> APP1
    HOST2 --> APP2
    HOST3 --> APP3
    
    style MEO fill:#4CAF50,stroke:#333,stroke-width:3px,color:#fff
    style MEPM1 fill:#2196F3,stroke:#333,stroke-width:2px,color:#fff
    style MEPM2 fill:#2196F3,stroke:#333,stroke-width:2px,color:#fff
    style MEPM3 fill:#2196F3,stroke:#333,stroke-width:2px,color:#fff
```

**Use Case:** Enterprise edge with multi-vendor infrastructure  
**Benefits:** Unified orchestration, leverage existing MEPMs, heterogeneous support

### 3.2 Model 2: Nuvla Full Stack

```mermaid
graph TB
    subgraph "Cloud / Datacenter"
        MEO[Nuvla MEO]
    end
    
    subgraph "Edge Site 1"
        NB1[NuvlaBox<br/>+Enhanced Agent as MEPM<br/>+Minimal MEP]
        APP1A[App 1A]
        APP1B[App 1B]
    end
    
    subgraph "Edge Site 2"
        NB2[NuvlaBox<br/>+Enhanced Agent as MEPM<br/>+Minimal MEP]
        APP2A[App 2A]
        APP2B[App 2B]
    end
    
    subgraph "Edge Site 3"
        NB3[NuvlaBox<br/>+Enhanced Agent as MEPM<br/>+Minimal MEP]
        APP3A[App 3A]
    end
    
    MEO -->|Mm5| NB1
    MEO -->|Mm5| NB2
    MEO -->|Mm5| NB3
    
    NB1 --> APP1A
    NB1 --> APP1B
    NB2 --> APP2A
    NB2 --> APP2B
    NB3 --> APP3A
    
    style MEO fill:#4CAF50,stroke:#333,stroke-width:3px,color:#fff
    style NB1 fill:#FF9800,stroke:#333,stroke-width:2px,color:#fff
    style NB2 fill:#FF9800,stroke:#333,stroke-width:2px,color:#fff
    style NB3 fill:#FF9800,stroke:#333,stroke-width:2px,color:#fff
```

**Use Case:** Greenfield IoT/Edge deployment with Nuvla ecosystem  
**Benefits:** Single vendor solution, simplified management, tight integration

### 3.3 Model 3: Federated MEO (Future)

```mermaid
graph TB
    subgraph "Operator A Domain"
        MEO_A[Nuvla MEO A]
        EDGE_A1[Edge Host A1]
        EDGE_A2[Edge Host A2]
    end
    
    subgraph "Operator B Domain"
        MEO_B[Partner MEO B]
        EDGE_B1[Edge Host B1]
        EDGE_B2[Edge Host B2]
    end
    
    MEO_A <-->|Mm8<br/>Federation| MEO_B
    
    MEO_A --> EDGE_A1
    MEO_A --> EDGE_A2
    MEO_B --> EDGE_B1
    MEO_B --> EDGE_B2
    
    style MEO_A fill:#4CAF50,stroke:#333,stroke-width:3px,color:#fff
    style MEO_B fill:#4CAF50,stroke:#333,stroke-width:3px,color:#fff
```

**Use Case:** Multi-operator edge federation (MEC 040)  
**Benefits:** Cross-operator coordination, resource sharing, geographic distribution

---

## 4. Trust Domains

```mermaid
graph TB
    subgraph "Operator Trust Domain (High Trust)"
        direction TB
        MEO[Nuvla MEO]
        INFRA[Infrastructure<br/>Services]
        MEPM[MEPM]
        VIM[VIM]
        INTERNAL[Internal<br/>Monitoring]
        
        MEO --- INFRA
        MEO --- MEPM
        MEO --- VIM
        MEO --- INTERNAL
    end
    
    subgraph "Third-Party Trust Domain (Medium Trust)"
        direction TB
        APPS[Edge<br/>Applications]
        PKG[Application<br/>Packages]
        MEPM_EXT[External<br/>MEPM]
        
        APPS --- PKG
        APPS --- MEPM_EXT
    end
    
    subgraph "External Domain (Low Trust)"
        direction TB
        EXT_SVC[External<br/>Services]
        PUBLIC[Public<br/>Internet]
        CUSTOMER[Customer<br/>Systems]
        
        EXT_SVC --- PUBLIC
        EXT_SVC --- CUSTOMER
    end
    
    MEO -.->|Controlled<br/>Interface| APPS
    APPS -.->|External<br/>Interface| EXT_SVC
    MEO -.->|Public API| CUSTOMER
    
    style MEO fill:#4CAF50,stroke:#333,stroke-width:3px,color:#fff
    style APPS fill:#FFF59D,stroke:#333,stroke-width:2px
    style EXT_SVC fill:#FFCCBC,stroke:#333,stroke-width:2px
```

**Security Boundaries:**
- **Operator Domain:** Mutual TLS, internal network, high trust
- **Third-Party Domain:** API key auth, resource quotas, sandboxing
- **External Domain:** OAuth2, rate limiting, low trust

---

## 5. Application Lifecycle Flow

```mermaid
sequenceDiagram
    participant User as Customer<br/>(Portal)
    participant MEO as Nuvla MEO
    participant MEPM as MEPM
    participant Host as MEC Host
    participant App as Application
    
    Note over User,App: 1. On-board Package
    User->>MEO: POST /api/module<br/>(On-board package)
    MEO-->>User: 201 Created<br/>module/abc-123
    
    Note over User,App: 2. Instantiate Application
    User->>MEO: POST /api/deployment<br/>(Create instance)
    MEO-->>User: 201 Created<br/>deployment/xyz-456
    
    User->>MEO: POST /deployment/xyz-456/start<br/>(Instantiate)
    MEO->>MEO: Select MEPM<br/>(placement decision)
    MEO->>MEPM: POST /mm5/app-instances<br/>(Deploy request)
    MEPM->>Host: Deploy container
    Host->>App: Start application
    App-->>Host: Running
    Host-->>MEPM: Instance created
    MEPM-->>MEO: 201 Created<br/>instance-id
    MEO-->>User: 200 OK<br/>Status: STARTED
    
    Note over User,App: 3. Query Status
    User->>MEO: GET /api/deployment/xyz-456
    MEO->>MEPM: GET /mm5/app-instances/{id}
    MEPM-->>MEO: Status: RUNNING
    MEO-->>User: 200 OK<br/>State: STARTED
    
    Note over User,App: 4. Terminate
    User->>MEO: POST /deployment/xyz-456/stop
    MEO->>MEPM: DELETE /mm5/app-instances/{id}
    MEPM->>Host: Stop container
    Host->>App: Terminate
    App-->>Host: Stopped
    Host-->>MEPM: Terminated
    MEPM-->>MEO: 200 OK
    MEO-->>User: 200 OK<br/>Status: STOPPED
```

---

## 6. Reference Point (Interface) Overview

```mermaid
graph TB
    subgraph "System Level Interfaces"
        MEO[MEO<br/>Nuvla API Server]
        OSS[OSS]
        PORTAL[Portal/UI]
        VIM_SYS[VIM]
        MEPM[MEPM]
        REPO[Package Repo]
    end
    
    MEO -->|Mm1<br/>Operations| OSS
    MEO <-->|Mm2<br/>Infrastructure<br/>Query| VIM_SYS
    MEO <-->|Mm3<br/>Customer API<br/>✅ Implemented| PORTAL
    MEO <-->|Mm5<br/>Platform Mgmt<br/>⚠️ To Implement| MEPM
    MEO <-->|Mm9<br/>Package Mgmt<br/>✅ Implemented| REPO
    
    style MEO fill:#4CAF50,stroke:#333,stroke-width:3px,color:#fff
    style PORTAL fill:#C8E6C9,stroke:#333,stroke-width:2px
    style REPO fill:#C8E6C9,stroke:#333,stroke-width:2px
    style MEPM fill:#FFCCBC,stroke:#333,stroke-width:2px
    style VIM_SYS fill:#E1F5FE,stroke:#333,stroke-width:2px
    style OSS fill:#F5F5F5,stroke:#999,stroke-width:1px,stroke-dasharray: 5 5
```

**Interface Status:**
- ✅ **Mm3** (Portal API) - Fully implemented
- ✅ **Mm9** (Package Repo) - Module API functional
- ⚠️ **Mm2** (VIM Query) - Partial (infrastructure service)
- ⚠️ **Mm5** (MEPM Communication) - To be standardized
- ❌ **Mm1** (OSS) - Out of scope (optional integration)

---

## 7. Mm5 Interface Protocol

```mermaid
sequenceDiagram
    participant MEO as Nuvla MEO
    participant MEPM as External MEPM
    
    Note over MEO,MEPM: Discovery & Registration
    MEPM->>MEO: POST /api/mepm<br/>(Register)
    MEO-->>MEPM: 201 Created<br/>mepm/uuid
    
    Note over MEO,MEPM: Capability Query
    MEO->>MEPM: GET /mm5/capabilities
    MEPM-->>MEO: {platforms: [k8s, docker],<br/>resources: {...}}
    
    Note over MEO,MEPM: Resource Query
    MEO->>MEPM: GET /mm5/resources
    MEPM-->>MEO: {cpu: 16, memory: 64GB,<br/>available: true}
    
    Note over MEO,MEPM: Application Instantiation
    MEO->>MEPM: POST /mm5/app-instances<br/>{app-pkg-id, config}
    MEPM->>MEPM: Deploy to host
    MEPM-->>MEO: 201 Created<br/>{instance-id, status}
    
    Note over MEO,MEPM: Status Monitoring
    loop Periodic polling
        MEO->>MEPM: GET /mm5/app-instances/{id}
        MEPM-->>MEO: {status: RUNNING,<br/>metrics: {...}}
    end
    
    Note over MEO,MEPM: Termination
    MEO->>MEPM: DELETE /mm5/app-instances/{id}
    MEPM->>MEPM: Stop application
    MEPM-->>MEO: 200 OK
```

---

## 8. Data Model Overview

```mermaid
erDiagram
    Module ||--o{ Deployment : instantiates
    Deployment ||--o{ DeploymentParameter : configures
    Deployment }o--|| NuvlaBox : "runs on"
    Deployment }o--|| InfrastructureService : "uses"
    NuvlaBox ||--o{ MEPM : "managed by"
    MEPM }o--|| Credential : "authenticated with"
    Deployment ||--o{ Event : generates
    Deployment ||--o{ Job : creates
    
    Module {
        string id PK
        string name
        string description
        object content
        array versions
        string acl
    }
    
    Deployment {
        string id PK
        string module FK
        string parent FK
        string state
        string acl
        timestamp created
    }
    
    NuvlaBox {
        string id PK
        string name
        object capabilities
        string location
        string status
        string acl
    }
    
    MEPM {
        string id PK
        string name
        string endpoint
        object capabilities
        object resources
        string status
    }
```

**Key Relationships:**
- **Module** (MEC: Application Package) → **Deployment** (MEC: Application Instance)
- **Deployment** runs on **NuvlaBox** (MEC: MEC Host)
- **NuvlaBox** managed by **MEPM** (MEC: Platform Manager)

---

## 9. Implementation Roadmap Visual

```mermaid
gantt
    title MEC 003 Implementation Timeline
    dateFormat  YYYY-MM-DD
    section Phase 1
    Architectural Mapping       :done, p1-1, 2025-10-21, 3d
    Terminology Guide          :done, p1-2, 2025-10-21, 2d
    Architecture Diagrams      :active, p1-3, 2025-10-23, 2d
    Documentation Updates      :active, p1-4, 2025-10-24, 2d
    Stakeholder Review         :p1-5, 2025-10-25, 2d
    
    section Phase 2
    MEPM Resource Schema       :p2-1, 2025-10-28, 3d
    MEPM CRUD API             :p2-2, 2025-10-30, 4d
    Mm5 Interface Spec        :p2-3, 2025-11-03, 3d
    Mm5 Client Implementation :p2-4, 2025-11-05, 4d
    
    section Phase 3
    Integration Testing        :p3-1, 2025-11-10, 4d
    Mock MEPM Testing         :p3-2, 2025-11-12, 3d
    Compliance Report         :p3-3, 2025-11-14, 3d
    Final Documentation       :p3-4, 2025-11-17, 3d
    Stakeholder Demo          :milestone, p3-5, 2025-11-20, 1d
```

**Timeline:** 4-6 weeks (21 Oct - 20 Nov 2025)  
**Current Status:** Phase 1, Week 1 (66% complete)

---

## 10. Alignment Progress

```mermaid
pie title MEC 003 Component Alignment
    "Fully Aligned (MEO, Package, Instance)" : 85
    "Good Alignment (Host, VIM, Portal)" : 10
    "Needs Implementation (MEPM, Mm5)" : 5
```

**Current Overall Alignment:** 75-80%  
**Target After Implementation:** 85-90%  
**Confidence Level:** ✅ HIGH

---

## Usage Notes

These diagrams are created using **Mermaid** syntax and can be:

1. **Rendered in GitHub/GitLab** - Automatically displayed in markdown
2. **Exported as Images** - Using Mermaid CLI or online tools
3. **Embedded in Presentations** - Export to PNG/SVG
4. **Updated Easily** - Text-based, version control friendly

**Recommended Tools:**
- [Mermaid Live Editor](https://mermaid.live/) - Online rendering
- VS Code Mermaid Extension - Local preview
- GitHub/GitLab - Native rendering

---

**Document Status:** ✅ Complete  
**Format:** Mermaid diagrams in Markdown  
**Next Steps:** Export to images for presentations if needed
