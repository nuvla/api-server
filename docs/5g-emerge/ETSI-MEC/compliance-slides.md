---
title: Nuvla as Foundation for an ETSI MEC Orchestrator
---

# Nuvla as Foundation for an ETSI MEC Orchestrator

Minimum Viable MEO based on Nuvla

---

## Agenda

- Nuvla platform overview
- Nuvla UI and core capabilities
- Mapping Nuvla to ETSI MEC MEO role
- Compliance study and adaptation gaps

---

## What is Nuvla?

- B2B edge management Platform as a Service (PaaS)
- Application-centric, hardware-agnostic, cloud-neutral
- Manages the full lifecycle of containerised apps at the edge and in the cloud
- Everything accessible via REST API for integration with OSS/BSS and CI/CD

More: https://docs.nuvla.io/

---

## Nuvla in a Nutshell

- Coordinates highly distributed edge infrastructure
- Handles governance, security, data management and app lifecycle
- Works with:
  - Edge devices (running NuvlaEdge)
  - Public clouds (AWS, Azure, GCP, etc.)
  - Private infrastructure (VMware, OpenStack, Kubernetes, …)

![Nuvla overview (illustrative)](https://sixsq.com/assets/img/nuvla/edge-as-a-service.svg)

---

## Core Concepts

- **NuvlaEdge**: edge runtime for IoT and compute workloads
- **Apps / Modules**: containerised applications managed via catalogue
- **Deployments**: concrete app instances on NuvlaEdges or clouds
- **Multi-cloud**: interfaces with many cloud providers; Nuvla does not run its own cloud

Docs: https://docs.nuvla.io/nuvla/ and https://docs.nuvla.io/nuvlaedge/

---

## Nuvla UI – Applications Catalogue

- Central catalogue of apps (modules)
- Stores configuration schema, images, defaults and metadata
- Basis for MEC application package catalogue

![Example app list (illustrative)](https://docs.nuvla.io/assets/images/applications-overview-960x540.png)

> Replace above with an actual screenshot from your Nuvla instance if available.

---

## Nuvla UI – Edge Inventory

- Global view of all NuvlaEdge devices
- Rich metadata: hardware, OS, resources, location
- Monitoring: health, connectivity, metrics

![NuvlaEdge list (illustrative)](https://docs.nuvla.io/assets/images/nuvlaedge-overview-960x540.png)

> Replace with screenshot of your own edge list view if preferred.

---

## Nuvla UI – Deployments & Lifecycle

- Create, update, delete app deployments on one or many edges
- Observe status, logs and KPIs
- Roll out updates safely across fleets

![Deployment view (placeholder)](https://docs.nuvla.io/assets/images/deployment-overview-960x540.png)

> Use an actual deployment detail screenshot from your environment if you have one.

---

## Governance and Security

- Fine-grained authentication and authorization (multi-tenant)
- Audit trail and event notifications
- Data tagging, replication and transfer mechanisms
- Hooks for:
  - Policy enforcement (e.g. OPA)
  - Image and artefact signature verification (DCT, cosign)

![Events / audit trail (placeholder)](https://docs.nuvla.io/assets/images/events-overview-960x540.png)

---

## Nuvla API & Integration

- REST API for:
  - Apps, edges, deployments, users, groups, data records, etc.
- Enables:
  - Integration into OSS/BSS
  - CI/CD pipelines
  - Custom dashboards and portals

API docs: https://docs.nuvla.io/tutorials/api/

---

## From Nuvla to MEC Orchestrator

- Nuvla already provides:
  - App catalogue and lifecycle at the edge
  - Edge inventory and monitoring
  - Governance, security and data management
  - Full REST API to drive operations
- ETSI MEC adds:
  - Formal roles (MEO, MEPM, OSS)
  - Standard reference points (Mm1, Mm3, …)
  - Standard data models (MEC 010‑2, MEC 037, …)

---

## ETSI MEC Standards in Scope

- **MEC 003** – Framework and Reference Architecture
- **MEC 010‑2** – Application lifecycle, rules and requirements management
- **MEC 037** – Application Package format and descriptor
- **MEC 011** – Application enablement
- **MEC 021** – Application Mobility Service API
- Scope of this work: **Minimum Viable MEC Orchestrator (MEO)**

---

## MEC Orchestrator Responsibilities (MEC 003 §7.1.4.1)

The MEO is responsible for:

- Maintaining an overall view of the MEC system
- On-boarding of application packages
- Selecting appropriate MEC host(s) for application instantiation
- Triggering application instantiation and termination
- Triggering application relocation (when supported)
- Coordinating with OSS the application instantiation lifecycle

(Extracted from MEC 003 §7.1.4.1)

---

## Compliance Study – Document Overview

We prepared a written **Compliance Study**:

- `Compliance Study of Nuvla Edge Orchestration Solution`
- Assesses Nuvla against ETSI MEC standards as a MEC Orchestrator
- Focused on:
  - MEC 003 roles and reference points
  - MEC 010‑2 lifecycle and data models
  - MEC 037 application packages
- Scope: **Minimum Viable MEO** based on existing Nuvla capabilities

---

## Mapping Responsibilities to Specs & Reference Points

For each MEO responsibility, the study:

- Identifies relevant ETSI specs (MEC 003, 010‑2, 011, 021, 037)
- Maps to:
  - **Mm1 (MEO–OSS)**: northbound interface (OSS as client)
  - **Mm3 (MEO–MEPM)**: southbound interface (MEO as client)
- Clarifies Nuvla’s role:
  - Where existing capabilities already match
  - Where MEC-specific behaviour needs to be added

---

## Example Mapping – On-boarding of Application Packages

- **Existing Nuvla capability**
  - Module/app catalogue, versioning, configuration, deployment
- **ETSI MEC alignment**
  - MEC 003, 010‑2, 011, 037
  - Use MEC 037 descriptors in catalogue
  - Use **Mm1** for OSS-driven onboarding requests
  - Use **Mm3** to prepare hosts/VIM for supported apps

Details in section:  
`Mapping to ETSI MEC Specifications and Reference Points`  
of the compliance study.

---

## Example Mapping – Lifecycle & Placement

- **Instantiation/termination**
  - Map Nuvla deployments to MEC 010‑2 LCM (AppInstanceInfo, InstantiateAppRequest, AppLcmOpOcc, …)
  - Expose lifecycle operations over:
    - **Mm1** (OSS → MEO)
    - **Mm3** (MEO → MEPM)
- **Placement**
  - Use NuvlaEdge inventory as MEC host view
  - Add MEC-specific attributes (services, zones, policies)
  - Implement placement engine using app requirements and host capabilities

---

## Gap Analysis vs. MEC Orchestrator Responsibilities

The study’s conclusion:

- Nuvla already provides strong primitives:
  - Catalogue, edge inventory, deployments, jobs, events, ACLs
- Gaps are mainly:
  - **MEC-compliant control surfaces** (Mm1/Mm3 APIs)
  - **MEC data models** (MEC 010‑2, 037)
  - **Standardised workflows** (placement, relocation, policy validation, signature checks)

---

## Key Adaptation Items (1/2)

1. **MEC‑compliant onboarding workflow**
   - Add MEC 037 descriptors to app catalogue
   - Define OSS‑driven onboarding API over Mm1
   - Integrate onboarding with host preparation over Mm3

2. **MEC 010‑2 lifecycle layer**
   - Surface deployments via MEC lifecycle API & models
   - Clean mapping between MEC operations and existing Nuvla resources

3. **Standardised Mm1 northbound interface**
   - Profile Nuvla’s APIs as a formal Mm1 interface
   - Allow OSS to submit orders/policies and query lifecycle status, alarms, performance

---

## Key Adaptation Items (2/2)

4. **Placement engine & MEC host profiling**
   - Profile NuvlaEdge devices as MEC hosts with MEC attributes
   - Deterministic, policy-compliant host selection

5. **Standardised relocation orchestration**
   - Build MEC‑style relocation on top of existing migration flows

6. **Policy enforcement in the pipeline**
   - Integrate a policy engine (e.g. OPA) into onboarding and instantiation

7. **End‑to‑end signature enforcement**
   - Generalise Docker Content Trust into a uniform integrity solution (DCT, Notary, cosign)

---

## Takeaways

- Nuvla already covers many of the **functional building blocks** of an MEO.
- The Minimum Viable MEO is mainly about:
  - Adding MEC‑specific APIs and data models
  - Standardising workflows around existing capabilities
- The compliance study provides:
  - A precise mapping to ETSI specs and reference points
  - A prioritised list of adaptations for implementation.

---