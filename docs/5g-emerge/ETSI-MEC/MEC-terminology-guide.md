# MEC Terminology Guide
## Mapping Between ETSI MEC and Nuvla.io

**Document Version:** 1.0  
**Date:** 21 October 2025  
**Purpose:** Terminology mapping for MEC 003 compliance  
**Audience:** Developers, integrators, documentation writers

---

## 1. Introduction

This guide provides bidirectional terminology mapping between **ETSI MEC standards** and **Nuvla.io** platform concepts. It helps:

- Nuvla developers understand MEC terminology
- MEC practitioners understand how to use Nuvla
- Documentation writers use consistent terminology
- Integration teams map between systems

---

## 2. Core Component Terminology

### 2.1 MEC → Nuvla Mapping

| MEC Term | Nuvla Term | Description |
|----------|------------|-------------|
| **MEO (MEC Orchestrator)** | **Nuvla API Server** | System-level orchestrator managing applications across multiple hosts |
| **MEPM (MEC Platform Manager)** | **External MEPM** or **Enhanced NuvlaBox Agent** | Host-level platform manager (external in MEO-only scope) |
| **MEP (MEC Platform)** | **Not implemented** | Platform services layer (out of scope for MEO-only) |
| **MEC Host** | **NuvlaBox** | Physical or virtual edge infrastructure device |
| **VIM (Virtualized Infrastructure Manager)** | **Infrastructure Service** | Manages compute resources (K8s, Docker, cloud) |
| **MEC Application** | **Deployment** | Running application instance on edge |
| **Application Package** | **Module** | Application definition with metadata and container images |
| **Application Descriptor (AppD)** | **Module specification** | JSON/YAML describing application requirements |
| **Application Context** | **Deployment parameters** | Runtime configuration and environment variables |
| **User App LCM Proxy** | **REST API + UI** | Customer-facing lifecycle management interface |
| **MEC System** | **Nuvla Platform** | Complete edge orchestration system |
| **OSS (Operations Support System)** | **External integration** | Optional integration via webhooks/events |

### 2.2 Nuvla → MEC Mapping

| Nuvla Term | MEC Term | Notes |
|------------|----------|-------|
| **Nuvla API Server** | **MEO** | System orchestrator |
| **NuvlaBox** | **MEC Host** | Edge device |
| **NuvlaBox Agent** | **MEPM (partial)** | Can be enhanced or replaced with external MEPM |
| **Module** | **Application Package** | App definition |
| **Deployment** | **MEC Application Instance** | Running app |
| **Deployment parameters** | **Application Context** | Runtime config |
| **Infrastructure Service** | **VIM** | Compute resource manager |
| **Credential** | **Authentication Info** | Access credentials |
| **Job** | **Lifecycle Operation** | Asynchronous operation |
| **Event** | **Notification** | System event |
| **Deployment Set** | **Multi-host Application** | App distributed across hosts |

---

## 3. Lifecycle Terminology

### 3.1 Application Lifecycle States

| MEC State | Nuvla State | Description |
|-----------|-------------|-------------|
| **NOT_INSTANTIATED** | **Created (not started)** | Package exists, not deployed |
| **INSTANTIATED** | **Started** | Application running |
| **TERMINATED** | **Stopped** | Application terminated |
| **PENDING** | **Pending** | Operation in progress |
| **ERROR** | **Error** | Deployment failed |

### 3.2 Lifecycle Operations

| MEC Operation | Nuvla Operation | API Endpoint |
|---------------|-----------------|--------------|
| **Instantiate** | **Start** | `POST /api/deployment/{id}/start` |
| **Terminate** | **Stop** | `POST /api/deployment/{id}/stop` |
| **Query** | **Get** | `GET /api/deployment/{id}` |
| **Scale** | **Update** | `PUT /api/deployment/{id}` |
| **Operate (start/stop)** | **Start/Stop** | Actions on deployment |

### 3.3 Package Management

| MEC Operation | Nuvla Operation | API Endpoint |
|---------------|-----------------|--------------|
| **On-board Package** | **Create Module** | `POST /api/module` |
| **Delete Package** | **Delete Module** | `DELETE /api/module/{id}` |
| **Query Package** | **Get Module** | `GET /api/module/{id}` |
| **Update Package** | **Create Version** | `POST /api/module` (new version) |

---

## 4. Interface Terminology (Reference Points)

### 4.1 System-Level Interfaces (Mm*)

| MEC Interface | Nuvla Implementation | Purpose |
|---------------|----------------------|---------|
| **Mm1** (MEO ↔ OSS) | External integration via webhooks | Operations support |
| **Mm2** (MEO ↔ VIM) | Infrastructure Service API | Query infrastructure resources |
| **Mm3** (MEO ↔ Portal) | REST API (`/api/*`) | Customer-facing API |
| **Mm4** (MEO ↔ UALCMP) | Combined with Mm3 | Lifecycle management |
| **Mm5** (MEO ↔ MEPM) | To be implemented | MEO to platform manager |
| **Mm6** (MEPM ↔ UALCMP) | Not applicable (external MEPM) | Platform-level operations |
| **Mm7** (UALCMP ↔ Portal) | Web UI | User interface |
| **Mm8** (MEO ↔ MEO) | Future (MEC 040) | Federation |
| **Mm9** (MEO ↔ Package Repo) | Module API | Package repository |

### 4.2 Platform-Level Interfaces (Mp*)

| MEC Interface | Nuvla Implementation | Status |
|---------------|----------------------|--------|
| **Mp1** (MEP ↔ App) | Not implemented | Out of scope (MEO-only) |
| **Mp2** (MEPM ↔ MEP) | Not implemented | External MEPM responsibility |
| **Mp3** (MEPM ↔ VIM) | External MEPM manages | Delegated to MEPM |

---

## 5. Resource Terminology

### 5.1 Compute Resources

| MEC Term | Nuvla Term | Description |
|----------|------------|-------------|
| **Virtualized Compute Resource** | **Container** or **VM** | Compute unit |
| **Compute Descriptor** | **Resource requirements** | CPU, memory, storage specs |
| **Resource Zone** | **Infrastructure Service** | Logical grouping of resources |
| **Availability Zone** | **NuvlaBox location** | Geographic location |

### 5.2 Storage Resources

| MEC Term | Nuvla Term | Description |
|----------|------------|-------------|
| **Virtualized Storage Resource** | **Volume** | Persistent storage |
| **Storage Descriptor** | **Volume specification** | Size, type, access mode |
| **Object Storage** | **S3 integration** | Object storage (external) |

### 5.3 Network Resources

| MEC Term | Nuvla Term | Description |
|----------|------------|-------------|
| **Virtual Network** | **Docker network** or **K8s network** | Container networking |
| **Traffic Rules** | Not implemented (MEP service) | Traffic steering (out of scope) |
| **DNS Rules** | Not implemented (MEP service) | DNS configuration (out of scope) |

---

## 6. Security & Multi-Tenancy Terminology

### 6.1 Authentication & Authorization

| MEC Term | Nuvla Term | Description |
|----------|------------|-------------|
| **OAuth2 Token** | **Session token** or **API key** | Authentication credential |
| **RBAC (Role-Based Access Control)** | **ACL (Access Control List)** | Authorization model |
| **User** | **User** | End user account |
| **Tenant** | **Customer organization** | Multi-tenant isolation |

### 6.2 Trust Domains

| MEC Term | Nuvla Mapping | Description |
|----------|---------------|-------------|
| **Operator Trust Domain** | Nuvla internal components | Nuvla server, agents, infrastructure |
| **Third-Party Trust Domain** | Edge applications, modules | Customer workloads |
| **External Domain** | Public internet, external services | Untrusted external systems |

---

## 7. Operational Terminology

### 7.1 Monitoring & Logging

| MEC Term | Nuvla Term | Description |
|----------|------------|-------------|
| **Performance Indicator** | **Metric** | System measurement |
| **Fault Management** | **Event** + **Notification** | Error tracking |
| **Log** | **Container logs** | Application logs |

### 7.2 Events & Notifications

| MEC Term | Nuvla Term | API Resource |
|----------|------------|--------------|
| **Lifecycle Change Notification** | **Event (state change)** | `/api/event` |
| **Performance Notification** | **Metric event** | `/api/event` |
| **Fault Notification** | **Event (severity: high)** | `/api/event` |

---

## 8. Deployment Terminology

### 8.1 Deployment Patterns

| MEC Term | Nuvla Term | Description |
|----------|------------|-------------|
| **Single-host Deployment** | **Deployment on NuvlaBox** | App on one edge device |
| **Multi-host Deployment** | **Deployment Set** | App distributed across multiple hosts |
| **Federated Deployment** | Future (MEC 040) | Cross-operator deployment |

### 8.2 Placement

| MEC Term | Nuvla Term | Description |
|----------|------------|-------------|
| **Placement Constraint** | **Deployment filter** | Host selection criteria |
| **Affinity** | **Placement policy** | Prefer certain hosts |
| **Anti-Affinity** | **Placement policy** | Avoid certain hosts |

---

## 9. Common Acronyms

### 9.1 MEC Acronyms

| Acronym | Full Name | Description |
|---------|-----------|-------------|
| **MEC** | Multi-access Edge Computing | ETSI edge computing standard |
| **MEO** | MEC Orchestrator | System-level orchestrator |
| **MEPM** | MEC Platform Manager | Host-level platform manager |
| **MEP** | MEC Platform | Runtime platform services |
| **VIM** | Virtualized Infrastructure Manager | Infrastructure resource manager |
| **OSS** | Operations Support System | Operator management systems |
| **UALCMP** | User Application Lifecycle Management Proxy | Customer-facing lifecycle API |
| **AppD** | Application Descriptor | Application specification |
| **UE** | User Equipment | End-user device (mobile, IoT) |
| **NFV** | Network Functions Virtualization | Virtualized network functions |
| **ETSI** | European Telecommunications Standards Institute | Standards body |

### 9.2 Nuvla Acronyms

| Acronym | Full Name | Description |
|---------|-----------|-------------|
| **API** | Application Programming Interface | REST API |
| **ACL** | Access Control List | Resource permissions |
| **RBAC** | Role-Based Access Control | Authorization model |
| **SSO** | Single Sign-On | OIDC authentication |
| **CIMI** | Cloud Infrastructure Management Interface | Original API inspiration |
| **COE** | Container Orchestration Engine | K8s, Docker Swarm |

---

## 10. Usage Examples

### 10.1 Example 1: Creating an Application

**MEC Terminology:**
> "On-board an Application Package to the MEO, then instantiate an Application Instance on a MEC Host via MEPM coordination."

**Nuvla Terminology:**
> "Create a Module in Nuvla, then start a Deployment on a NuvlaBox via the API."

**API Calls:**
```
# On-board package
POST /api/module
{
  "name": "my-edge-app",
  "description": "MEC application",
  "content": {
    "image": "my-app:v1.0",
    "ports": [{"target": 8080}]
  }
}

# Instantiate application
POST /api/deployment
{
  "module": {"href": "module/abc-123"},
  "parent": "nuvlabox/xyz-789"
}

# Start instance
POST /api/deployment/dep-456/start
```

### 10.2 Example 2: Querying Host Resources

**MEC Terminology:**
> "Query VIM via Mm2 to get available virtualized compute resources on MEC Hosts."

**Nuvla Terminology:**
> "Query Infrastructure Services to get available resources on NuvlaBoxes."

**API Calls:**
```
# List infrastructure services
GET /api/infrastructure-service

# Get NuvlaBox details (resources)
GET /api/nuvlabox/xyz-789
```

### 10.3 Example 3: Application Lifecycle

**MEC Terminology:**
> "Perform lifecycle operations: Instantiate, Query status, Terminate."

**Nuvla Terminology:**
> "Perform deployment actions: Start, Get status, Stop."

**API Calls:**
```
# Start (instantiate)
POST /api/deployment/dep-456/start

# Query status
GET /api/deployment/dep-456

# Terminate (stop)
POST /api/deployment/dep-456/stop
```

---

## 11. Documentation Guidelines

### 11.1 For Nuvla Documentation

When documenting for **MEC-aware audience**, use this pattern:

```markdown
## Creating an Application (MEC: On-boarding Package)

Create a **Module** (MEC: Application Package) that defines your application...

POST /api/module (MEC Mm3: PackageManagement API)
```

### 11.2 For MEC Documentation

When documenting for **Nuvla users**, use this pattern:

```markdown
## Application Package Management

In MEC terminology, a Nuvla **Module** is called an **Application Package**.
The Module API implements the MEC Mm9 reference point for package management.
```

### 11.3 Dual Terminology Template

For maximum clarity, use dual terminology:

```
Feature: Application Deployment
MEC Term: Application Instance
Nuvla Term: Deployment
API: /api/deployment
Interface: Mm3 (MEO ↔ Portal)
```

---

## 12. API Mapping Quick Reference

### 12.1 MEC Lifecycle Operations → Nuvla API

| MEC Operation | HTTP Method | Nuvla Endpoint |
|---------------|-------------|----------------|
| On-board Package | POST | `/api/module` |
| Query Package | GET | `/api/module/{id}` |
| Delete Package | DELETE | `/api/module/{id}` |
| Instantiate App | POST | `/api/deployment` + `/api/deployment/{id}/start` |
| Query App Instance | GET | `/api/deployment/{id}` |
| Terminate App | POST | `/api/deployment/{id}/stop` |
| Scale App | PUT | `/api/deployment/{id}` (update replicas) |
| Query Hosts | GET | `/api/nuvlabox` |
| Query VIM | GET | `/api/infrastructure-service` |

### 12.2 Nuvla Resources → MEC Concepts

| Nuvla Resource | MEC Concept | RESTful Path |
|----------------|-------------|--------------|
| `module` | Application Package | `/api/module` |
| `deployment` | Application Instance | `/api/deployment` |
| `deployment-parameter` | Application Context | `/api/deployment-parameter` |
| `nuvlabox` | MEC Host | `/api/nuvlabox` |
| `infrastructure-service` | VIM | `/api/infrastructure-service` |
| `credential` | Authentication Info | `/api/credential` |
| `event` | Notification | `/api/event` |
| `job` | Lifecycle Operation | `/api/job` |

---

## 13. Glossary

### 13.1 Combined Glossary (MEC + Nuvla)

| Term | Type | Definition |
|------|------|------------|
| **Application Context** | MEC | Runtime configuration for an application instance |
| **Application Descriptor (AppD)** | MEC | Specification describing application requirements |
| **Application Package** | MEC | Deployable unit containing application code and metadata |
| **Credential** | Nuvla | Authentication information for accessing resources |
| **Deployment** | Nuvla | Running instance of an application (MEC: Application Instance) |
| **Deployment Set** | Nuvla | Group of deployments across multiple hosts |
| **Event** | Nuvla | System notification about state changes or errors |
| **Infrastructure Service** | Nuvla | External compute platform (MEC: VIM) |
| **Job** | Nuvla | Asynchronous operation tracked by the system |
| **MEC Host** | MEC | Physical or virtual edge infrastructure device |
| **MEPM** | MEC | MEC Platform Manager - host-level orchestrator |
| **MEO** | MEC | MEC Orchestrator - system-level orchestrator |
| **MEP** | MEC | MEC Platform - runtime platform services |
| **Module** | Nuvla | Application definition (MEC: Application Package) |
| **NuvlaBox** | Nuvla | Edge device (MEC: MEC Host) |
| **Placement** | Both | Decision of which host runs an application |
| **Reference Point** | MEC | Standardized interface between components (Mm*, Mp*) |
| **Trust Domain** | MEC | Security boundary defining trust relationships |
| **VIM** | MEC | Virtualized Infrastructure Manager |

---

## 14. Conclusion

This terminology guide provides a comprehensive mapping between ETSI MEC and Nuvla.io concepts. Key takeaways:

1. **Nuvla API Server = MEO** - This is the primary mapping
2. **Module = Application Package** - Application definitions
3. **Deployment = Application Instance** - Running applications
4. **NuvlaBox = MEC Host** - Edge infrastructure
5. **Mm3 = REST API** - Customer-facing interface

When in doubt:
- Use **MEC terminology** for external/standards documentation
- Use **Nuvla terminology** for internal documentation and code
- Use **both** when clarity is needed

---

## Appendix: Terminology Conversion Cheat Sheet

```
MEC                     →    Nuvla
─────────────────────────────────────────
MEO                     →    Nuvla API Server
Application Package     →    Module
Application Instance    →    Deployment
MEC Host                →    NuvlaBox
MEPM                    →    External MEPM / Enhanced Agent
VIM                     →    Infrastructure Service
AppD                    →    Module specification
Application Context     →    Deployment parameters
Mm3                     →    REST API
Mm5                     →    To be implemented
Instantiate             →    Start
Terminate               →    Stop
On-board                →    Create (module)
```

---

**Document Status:** ✅ Complete  
**Usage:** Reference for all MEC 003 implementation work  
**Maintenance:** Update as new mappings are discovered
