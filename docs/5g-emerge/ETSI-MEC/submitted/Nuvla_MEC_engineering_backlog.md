# Nuvla MEC Engineering Backlog

## Purpose

This document translates `Nuvla_MEC_gap_closure_plan.md` into an execution-oriented backlog. It is intended to support milestone planning, sprint slicing, assignment of concrete engineering work, and agent hand-off.

The backlog is organized around the staged development approach:

1. stabilize the already-claimed standards-facing surface
2. connect that surface to real Nuvla resources and southbound orchestration
3. complete onboarding, validation, and evidence collection
4. defer advanced features until the minimum viable ETSI MEC MEO is credible and testable

## Recommended Hand-Off Mode

For execution, this document should be read and assigned in `milestone-first` order, with workstreams aligned to the same sequence.

Reason:

- milestones provide the delivery sequence and dependency boundaries
- aligned workstreams make the thematic structure agree with the execution order
- this avoids a split between "topic grouping" and "delivery order"

Practical rule:

- use `milestones` as the primary hand-off order
- use `workstreams` as the matching execution-stream labels for the same ordered steps

## Workstream Tags Used in Hand-Off

The workstreams are intentionally aligned with milestone order. When tasks are handed off, use the following labels:

- `WS1` Baseline and MVP scope lock
- `WS2` Northbound Mm1 lifecycle hardening
- `WS3` Southbound MEPM coordination and placement
- `WS4` MEC 037 package onboarding
- `WS5` Validation readiness: subscriptions, notifications, evidence, and security
- `WS6` Deferred and advanced features

## Planning Assumptions

- Priority is given to closing the gap between claimed and actual behavior.
- The primary target is a minimum viable ETSI MEC MEO.
- 3GPP alignment is achieved through ETSI MEC-aligned interfaces, not through native 3GPP MnS/NRM work.
- Advanced items such as relocation, grant management, and cancel/fail/retry are deferred unless they become release-critical.

## Backlog by Milestone

## Milestone 1: Baseline Locked

### Goal

Freeze the exact compliance subset to implement and validate.

### Key tasks

- `WS1` Reconcile the implementation claims in the compliance note with actual code behavior.
- `WS1` Freeze the selected standards subset for the MVP:
  - `MEC 003` orchestrator role
  - `MEC 010-2` lifecycle and subscriptions
  - selected `MEC 011` data-model elements
  - selected `MEC 037` onboarding and package metadata behavior
- `WS1` Clarify and normalize the southbound reference-point naming used across code and docs.
- `WS1` Create the first requirement-to-test traceability draft.
- `WS1` Define explicit deferred items for the MVP release claim.

### Main artifacts

- `submitted/Nuvla_MEC_gap_closure_plan.md`
- `submitted/Nuvla_MEC_validation_traceability_draft.md`
- updated compliance note and/or internal implementation-status annex in a later step

### Exit criteria

- one agreed MVP claim set
- one agreed deferred-feature list
- one approved traceability draft

## Milestone 2: Minimum Viable MEC Lifecycle

### Goal

Turn the northbound lifecycle surface into real functionality backed by Nuvla deployments and jobs.

### Primary code touchpoints

- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/app_lcm_v2.clj`
- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/app_instance.clj`
- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/app_lcm_op_occ.clj`
- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/app_lcm_op_tracking.clj`
- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/error_handler.clj`

### Key tasks

- `WS2` Replace placeholder `app_instances` handlers with real deployment CRUD integration:
  - create app instance
  - list app instances
  - get app instance
  - delete app instance
- `WS2` Replace placeholder `app_lcm_op_occs` handlers with real job-backed retrieval and query logic.
- `WS2` Standardize use of `ProblemDetails` across all northbound MEC handlers.
- `WS2` Ensure MEC state mapping is consistent for:
  - instance state
  - operation state
  - invalid transitions
  - not-found and conflict responses
- `WS2` Define ownership and query-filter behavior for list endpoints.
- `WS2` Add focused tests for:
  - CRUD success
  - not-found
  - malformed request
  - invalid transition
  - operation query behavior

### Dependencies

- Milestone 1 standards subset must be frozen.

### Exit criteria

- lifecycle resources are no longer placeholder-based
- operation occurrences are backed by persisted Nuvla job state
- API responses are stable enough for conformance and integration tests

## Milestone 3: Deterministic Southbound Orchestration

### Goal

Replace hardcoded target resolution with real MEPM-aware orchestration and basic placement.

### Primary code touchpoints

- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/lifecycle_handler.clj`
- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/mm3_client.clj`
- `api-server/code/src/com/sixsq/nuvla/server/resources/mepm.clj`
- MEC host profiling code to be added or adapted near NuvlaBox/MEPM integration points

### Key tasks

- `WS3` Replace the hardcoded default MEPM endpoint resolution.
- `WS3` Use the existing `mepm` resource as the southbound registry of reachable platform managers.
- `WS3` Define host-to-MEPM association rules.
- `WS3` Implement a first deterministic placement policy using:
  - host targeting inputs
  - architecture
  - CPU
  - RAM
  - disk
  - required services or capabilities where available
- `WS3` Decide whether grants are:
  - minimally implemented
  - stubbed behind explicit non-support
  - fully deferred from the MVP claim
- `WS3` Ensure southbound failures map to clear northbound errors and logs.

### Dependencies

- Milestone 2 lifecycle persistence and error handling
- available or modeled MEPM inventory

### Exit criteria

- instantiate/terminate use registry-backed southbound resolution
- placement decisions are deterministic and observable
- placement failures can be validated and evidenced

## Milestone 4: Interoperable Package Onboarding

### Goal

Move from module bootstrap behavior to a credible MEC 037-aligned onboarding subset.

### Primary code touchpoints

- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/app_package.clj`
- new route handlers or supporting namespaces for:
  - descriptor retrieval
  - package-content retrieval/upload
  - onboarding validation
- module subtype/spec definitions that hold MEC AppD metadata

### Key tasks

- `WS4` Implement missing package descriptor resource behavior.
- `WS4` Implement missing package content resource behavior.
- `WS4` Define the supported onboarding flow and state transitions.
- `WS4` Validate required metadata and descriptor structure.
- `WS4` Persist and expose:
  - onboarding state
  - versioning
  - provenance
  - checksum/integrity metadata
- `WS4` Decide which artifact formats are in scope for the MVP and which are not.
- `WS4` Add focused negative tests for:
  - missing metadata
  - malformed descriptor
  - unsupported package format
  - integrity/policy failure where implemented

### Dependencies

- Milestone 1 standards subset freeze
- coordination with security/policy decisions from Milestone 5 where needed

### Exit criteria

- package resources exposed in docs are actually implemented
- onboarding is traceable and testable
- package metadata is sufficient for lifecycle and validation flows

## Milestone 5: Notifications, Evidence, and Security Gates

### Goal

Make the standards-facing flows observable, auditable, and validation-ready.

### Primary code touchpoints

- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/app_lcm_subscription.clj`
- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/notification_dispatcher.clj`
- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/app_lcm_v2.clj`
- onboarding and lifecycle handlers where security and policy checks are inserted

### Key tasks

- `WS5` Replace in-memory subscriptions with persistent resource-backed behavior.
- `WS5` Wire notification dispatch to actual state transitions for:
  - app instance changes
  - operation occurrence changes
  - package changes if still in promised scope
- `WS5` Expose enough audit trail to support validation evidence.
- `WS5` Insert policy checks before instantiation and, where necessary, onboarding.
- `WS5` Enforce authentication and authorization consistently on MEC-facing routes.
- `WS5` Add package integrity validation for supported formats.
- `WS5` Add targeted tests for:
  - subscription lifecycle
  - matching and non-matching notifications
  - authz failures
  - policy rejection
  - evidence/log availability

### Dependencies

- Milestone 2 real lifecycle state
- Milestone 3 real southbound orchestration
- Milestone 4 onboarding semantics

### Exit criteria

- notifications are durable enough to support validation
- security and policy behavior is testable
- validation campaign can collect the evidence listed in the validation strategy

## Milestone 6: Deferred and Advanced Features

### Goal

Keep advanced scope visible without blocking the MVP.

### Candidate backlog items

- `WS6` `AppLcmOpOcc` cancel/fail/retry controls
- `WS6` grant lifecycle support
- `WS6` relocation and MEC 021 workflows
- `WS6` CAPIF-facing integration
- `WS6` deeper 3GPP telemetry normalization and assurance mappings

### Entry condition

- only start once Milestones 2 to 5 are stable and the MVP claim is defensible

## Cross-Cutting Backlog Items

## A. Testing and Validation Packaging

- Maintain requirement-to-test mapping as implementation evolves.
- Add conformance-style request/response checks for the selected MEC endpoints.
- Keep at least one positive and one negative scenario per implemented feature.
- Preserve sample API traces and logs for future compliance evidence packs.

## B. Documentation and Claim Management

- Update internal docs whenever behavior changes from placeholder to real implementation.
- Avoid claiming support for a resource until:
  - handler exists
  - persistence/integration exists
  - negative behavior is defined
  - at least one validation scenario exists

## C. Contract Consistency

- Align code comments, docs, and route descriptions on the same reference-point terminology.
- Keep one authoritative description of the selected MVP subset.

## Recommended Sprint Slicing

### Sprint slice 1

- Milestone 1
- Milestone 2 core CRUD integration

### Sprint slice 2

- Milestone 2 operation tracking completion
- Milestone 3 MEPM resolution and basic placement

### Sprint slice 3

- Milestone 4 descriptor and package-content resources
- Milestone 5 persistent subscriptions

### Sprint slice 4

- Milestone 5 security gates, policy enforcement, and evidence packaging

## Recommended Agent Execution Sequence

Use this section when handing work to the agent. The sequence is intentionally milestone-first, and each sequence step aligns with the matching workstream.

### Sequence 1

- complete `Milestone 1 / WS1`
- then start the `Milestone 2 / WS2` tasks

### Sequence 2

- finish `Milestone 2 / WS2` lifecycle CRUD and operation persistence
- only then start `Milestone 3 / WS3`

### Sequence 3

- once `Milestone 3 / WS3` has real southbound resolution, start `Milestone 4 / WS4`

### Sequence 4

- once `Milestone 4 / WS4` onboarding behavior is real, complete `Milestone 5 / WS5`

### Sequence 5

- only start `Milestone 6 / WS6` after the MVP claim is stable

## Definition of Done for Standards-Facing Features

A feature should only be considered done when all of the following are true:

- the endpoint or resource exists and is wired to real Nuvla state
- success and failure behavior are both defined
- the response model is standards-aligned for the chosen subset
- logs/events/evidence for validation are available
- at least one traceability entry links the feature to a validation scenario

## Immediate Next Tasks

The most valuable near-term tasks, in recommended hand-off order, are:

1. `Milestone 1 / WS1`: finalize the MVP claim boundary and deferred list
2. `Milestone 2 / WS2`: implement real `app_instances` handlers in `app_lcm_v2.clj`
3. `Milestone 2 / WS2`: implement real `app_lcm_op_occs` retrieval through jobs
4. `Milestone 3 / WS3`: replace hardcoded MEPM endpoint resolution
5. `Milestone 4 / WS4`: add descriptor and package-content resources to package management
6. `Milestone 5 / WS5`: remove in-memory subscription-only behavior

