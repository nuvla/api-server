# Nuvla MEC Gap Closure Plan

## Purpose

This document turns the current standards promise into an implementation roadmap by comparing:

- the target compliance and implementation claims in `5G-EMERGE_P2_TN_2.3.2 Compliance_of_the_orchestration_software v06-pre-release.md`
- the validation expectations in `Nuvla_validation_strategy_for_standards_compliance.md`
- the code currently present under `api-server/code/src/com/sixsq/nuvla/server/resources/mec`

The goal is to stage the work needed to close the gap between the promised ETSI MEC-aligned behavior and what is actually implemented today.

## Executive Summary

The current MEC codebase is a useful scaffold, not yet a completed standards-compliant layer.

What already exists:

- a recognizable namespace and API surface for MEC package management, lifecycle management, operation occurrences, subscriptions, filtering, and ProblemDetails
- object-mapping helpers between Nuvla resources and MEC-managed objects
- a southbound HTTP client for host/platform coordination
- a first pass at webhook notification dispatch

What is still missing for credible compliance:

- real persistence and CRUD integration for `AppInstanceInfo` and `AppLcmOpOcc`
- a real MEPM selection and placement flow instead of the current hardcoded endpoint path
- actual package onboarding semantics for MEC 037, including descriptor retrieval, package content handling, integrity checks, and traceability
- durable subscriptions and real event-driven notifications
- a requirement-to-test-to-evidence chain that matches the validation strategy

In practice, the fastest path is not to add more surface area first, but to complete and harden the APIs that already exist in code and are already claimed in the compliance note.

## Baseline Assessment

### 1. Mm1 application package management is only partially implemented

The compliance note claims support for package collection/item resources, descriptor access, package content access, subscriptions, and notifications.

The code currently provides only part of that:

- `app_package.clj` implements package query, get, create, and delete by mapping MEC packages to Nuvla modules
- package creation is still a placeholder-style module bootstrap, not a full MEC 037 onboarding flow
- links are emitted for `/appd` and `/package_content`, but no route handlers are implemented for those resources
- no package-management subscriptions or package-change notification flow exists in the `mec` code
- no signature verification, integrity validation, descriptor conformance validation, or onboarding state machine is implemented

### 2. Mm1 lifecycle management exists mostly as route scaffolding

The compliance note marks the main lifecycle resources as implemented, but the code shows major gaps:

- `app_lcm_v2.clj` exposes the expected lifecycle routes
- `POST /app_instances`, `GET /app_instances`, `GET /app_instances/{id}`, and `DELETE /app_instances/{id}` are still placeholders or return `501`
- operation-occurrence list/get handlers are also placeholders
- lifecycle operations call `lifecycle_handler.clj`, but the returned operation occurrence is synthetic and not persisted as a real job/resource
- subscriptions are stored in an in-memory atom rather than a persistent resource

This means the current northbound API shape is present, but the underlying lifecycle state is not yet trustworthy or auditable enough for compliance validation.

### 3. Southbound host coordination is present as a client, not as a finished orchestration flow

The current code includes useful southbound building blocks:

- `mm3_client.clj` implements health, capability, resource, and app lifecycle calls to an external platform manager
- `mepm.clj` elsewhere in the codebase already provides a persistent MEPM registry and actions to query health, capabilities, and resources

However, the actual orchestration path is incomplete:

- `lifecycle_handler.clj` resolves the target platform through a hardcoded default endpoint
- no deterministic host selection or placement engine exists
- no matching of AppD requirements against host capabilities/resources is implemented
- no grant flow is implemented even though grants are part of the target compliance picture
- comments still mix `Mm3` and `Mm5`, so the southbound contract needs to be clarified and frozen before more code is added

### 4. Operation tracking and notification semantics are not yet production-grade

The validation strategy requires observable lifecycle state, error behavior, and evidence collection.

Current status:

- `app_lcm_op_occ.clj` and `app_lcm_op_tracking.clj` define mappings and helper functions, but they remain placeholders for real job integration
- `notification_dispatcher.clj` can deliver webhooks with retry logic, but it is not wired into a durable event pipeline
- the event listener is explicitly a stub
- there is no persistent notification history or evidence-friendly audit path for validation campaigns

### 5. Security, policy, and onboarding validation are still largely absent

The validation note expects security checks in onboarding and instantiation.

The MEC code does not yet implement:

- package signature verification
- policy enforcement for host targeting, placement constraints, or operator policies
- admission checks that combine package metadata, host capabilities, and policy decisions
- structured traceability from rejected request to standards-aligned error and validation evidence

### 6. Optional and advanced items remain out of scope for the MVP

The promised documents explicitly or implicitly defer some areas that should stay in a later phase:

- cancel/fail/retry operation controls
- relocation and MEC 021 mobility workflows
- CAPIF and native 3GPP MnS/NRM support
- stateful relocation/session continuity behavior

These should remain outside the minimum viable compliance increment unless the project priorities change.

## Recommended Delivery Strategy

The delivery should follow one rule: complete the already-claimed compliance surface before expanding into advanced MEC and 3GPP features.

That means the order should be:

1. make the current Mm1 lifecycle endpoints real and testable
2. connect lifecycle orchestration to actual MEPM inventory and host selection
3. complete MEC 037-style package onboarding and package resources
4. harden notifications, policy checks, and validation evidence
5. only then address optional grant, relocation, and deeper 3GPP-facing features

## Workstreams

### Workstream 0: Compliance Baseline and Contract Freeze

Objective: establish one authoritative, code-grounded compliance baseline before more implementation proceeds.

Scope:

- reconcile the compliance note's "Yes" claims with actual code behavior
- freeze the naming and semantics of the southbound reference point used in code and docs
- define the minimum viable standards subset to be claimed for the next release
- create a requirement traceability matrix linking standard item, endpoint/resource, code owner, and validation scenario

Outputs:

- approved compliance baseline
- frozen API/resource list for the MVP
- traceability matrix for development and validation

### Workstream 1: Northbound Mm1 Lifecycle Hardening

Objective: turn the existing route skeleton into a real MEC 010-2 lifecycle facade over Nuvla deployments and jobs.

Scope:

- implement real `app_instances` create/list/get/delete using deployment CRUD
- persist and query `AppLcmOpOcc` through the job resource instead of synthetic maps
- standardize ProblemDetails usage across all MEC lifecycle handlers
- validate lifecycle transitions and idempotency
- ensure list/get responses expose consistent MEC state mappings

Outputs:

- fully wired `AppInstanceInfo` resource behavior
- fully wired `AppLcmOpOcc` persistence and retrieval
- reliable lifecycle state and error model

### Workstream 2: Southbound MEPM Coordination and Placement

Objective: replace the hardcoded southbound path with deterministic orchestration logic.

Scope:

- use the existing MEPM resource registry for endpoint resolution
- profile MEC hosts and associate them with MEPM entries and NuvlaBox resources
- implement a basic placement engine using host metadata, available resources, and app requirements
- add admission checks for CPU, RAM, disk, architecture, and required platform features
- define the minimal grant/authorization behavior for the selected compliance target, or explicitly defer it

Outputs:

- resolved target-host selection instead of default endpoint routing
- auditable placement decision path
- observable rejection reasons for unsuitable hosts

### Workstream 3: MEC 037 Package Onboarding Completion

Objective: move package management from module bootstrapping to standards-aligned onboarding.

Scope:

- implement descriptor retrieval and package content handlers
- support package import/upload semantics aligned with the chosen MEC 037 subset
- validate descriptors and required metadata before onboarding completes
- store versioning, provenance, checksum, and onboarding state transitions explicitly
- map package contents cleanly into Nuvla's module catalogue without losing MEC metadata

Outputs:

- complete package-resource behavior for the selected subset
- real onboarding state machine and validation failures
- package traceability suitable for validation evidence

### Workstream 4: Subscriptions, Notifications, and Operational Evidence

Objective: make lifecycle and onboarding workflows observable and auditable enough for the validation campaign.

Scope:

- replace in-memory subscriptions with persistent resources
- wire state-change notifications to real deployment/job/package events
- add package-management subscriptions if they remain in the promised scope
- persist delivery results or at least expose reliable delivery/audit logs
- align emitted events, logs, and API traces with the evidence list in the validation strategy

Outputs:

- durable subscriptions
- event-driven notification delivery
- evidence-ready lifecycle and onboarding traces

### Workstream 5: Security and Policy Enforcement

Objective: add the minimum enforcement needed to support credible standards-facing behavior.

Scope:

- add package integrity verification for the supported artifact formats
- enforce authentication and authorization rules consistently on MEC-facing endpoints
- insert a policy gate before instantiation and, where needed, during onboarding
- define operator-policy checks for host targeting, forbidden capabilities, and signature requirements
- return standards-aligned rejection errors for policy failures

Outputs:

- secure onboarding and instantiation gates
- policy-driven placement/admission control
- reproducible negative-test scenarios

### Workstream 6: Advanced and Deferred Features

Objective: keep non-MVP scope visible without blocking the minimum viable compliance increment.

Scope:

- operation cancel/fail/retry
- grants if strict compliance target requires them
- relocation and MEC 021 workflows
- CAPIF-facing exposure adapters
- deeper 3GPP telemetry normalization

Outputs:

- deferred backlog with acceptance conditions
- clear decision log for what is intentionally out of scope

## Milestones and Phases

### Milestone 1: Baseline Locked

Focus:

- Workstream 0

Exit criteria:

- the team agrees on what will be claimed in the next compliance report
- every claimed endpoint/resource has an owner and an implementation status
- the validation team has an initial requirement-to-test matrix

### Milestone 2: Minimum Viable MEC Lifecycle

Focus:

- Workstream 1
- the minimum slice of Workstream 4 needed for operation visibility

Exit criteria:

- app instance CRUD works end-to-end over Nuvla deployment resources
- instantiate and terminate create real operation occurrences backed by jobs
- operation status queries return real persisted state
- subscriptions are no longer process-local only

### Milestone 3: Deterministic Southbound Orchestration

Focus:

- Workstream 2

Exit criteria:

- lifecycle actions resolve real MEPM targets
- host/resource checks are enforced before deployment
- placement failures are explicit, reproducible, and visible in API responses and logs

### Milestone 4: Interoperable Package Onboarding

Focus:

- Workstream 3
- package-related parts of Workstream 4

Exit criteria:

- descriptor and package content resources are implemented
- onboarding validates the selected MEC 037 subset
- package metadata, versioning, and provenance are queryable and testable

### Milestone 5: Validation-Ready Compliance Layer

Focus:

- Workstream 4
- Workstream 5

Exit criteria:

- validation scenarios from `Nuvla_validation_strategy_for_standards_compliance.md` can be executed against real code paths
- positive, negative, and interoperability scenarios have evidence outputs
- the team can produce the required requirement-to-test mapping, execution report, and open-gap list

### Milestone 6: Advanced Alignment

Focus:

- Workstream 6

Exit criteria:

- deferred items are either implemented or explicitly excluded from the release claim
- advanced features do not weaken the previously validated MVP path

## Suggested Phase Plan

### Phase A: Stabilize the Claimed Surface

Primary goal:

- fix the gap between documentation claims and actual runtime behavior

Included milestones:

- Milestone 1
- Milestone 2

### Phase B: Make Orchestration Real

Primary goal:

- connect lifecycle control to real host selection and southbound coordination

Included milestones:

- Milestone 3

### Phase C: Make Packages Interoperable

Primary goal:

- complete the MEC 037 onboarding and package-resource story

Included milestones:

- Milestone 4

### Phase D: Make Compliance Demonstrable

Primary goal:

- produce the observability, security, and evidence needed for validation and external credibility

Included milestones:

- Milestone 5

### Phase E: Extend Beyond the MVP

Primary goal:

- address mobility, grants, cancel/fail/retry, and deeper 3GPP-facing features if still required

Included milestones:

- Milestone 6

## Priority Backlog

The highest-priority implementation items are:

- replace placeholder `app_instances` and `app_lcm_op_occs` handlers with real CRUD integration
- replace in-memory subscriptions with persistent resources
- replace hardcoded MEPM resolution with registry-backed target selection
- implement admission checks from app requirements to host capabilities/resources
- add missing package descriptor and package content resources
- add descriptor validation and package integrity checks
- wire notifications to real events and evidence collection

## Validation Mapping Guidance

To stay aligned with the validation strategy, each implemented item should immediately produce:

- one or more validation scenarios
- positive and negative test cases
- expected API traces
- expected logs/events
- a pointer to the standard requirement being covered

This is important because the project does not only need working code; it also needs defensible evidence for the final compliance claim.

## Recommendation

The recommended release target is a Minimum Viable ETSI MEC MEO centered on:

- Mm1 lifecycle operations backed by real Nuvla deployment and job resources
- deterministic southbound host coordination and placement checks
- a practical MEC 037 onboarding subset with explicit validation and traceability
- durable subscriptions, notifications, and validation evidence

Grant management, relocation, advanced operation control, and native 3GPP-facing adapters should remain explicitly deferred unless they become mandatory for the next project milestone.
