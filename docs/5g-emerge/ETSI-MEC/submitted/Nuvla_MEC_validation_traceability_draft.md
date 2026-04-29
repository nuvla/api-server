# Nuvla MEC Validation Traceability Draft

## Purpose

This draft provides the requirement-to-test mapping requested by `Nuvla_validation_strategy_for_standards_compliance.md`. It is intended to be updated as implementation progresses, but it is already structured to support:

- development prioritization
- validation planning
- evidence collection
- compliance reporting

The focus is the minimum viable ETSI MEC MEO scope described in `Nuvla_MEC_gap_closure_plan.md`.

## Usage Notes

- `Status` reflects the current code baseline, not the claimed future target.
- `Scenario ID` is a stable handle for test planning and evidence collection.
- `Evidence` lists the minimum expected proof set for a successful validation run.
- Entries marked `Deferred` should not be included in the MVP compliance claim unless implementation status changes.

## Traceability Matrix

| Req ID | Standard | Requirement / Capability | Current Status | Target Milestone | Scenario ID | Validation Objective | Minimum Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MEC-003-01 | MEC 003 | Nuvla behaves as a recognizable MEO for the selected lifecycle scope | Partial | M1 | SCN-ARCH-01 | Verify the exposed roles, interfaces, and managed objects match the selected MEO subset | architecture note, endpoint inventory, selected API traces |
| MEC-0102-01 | MEC 010-2 | `POST /app_instances` creates an application instance resource | Scaffold only | M2 | SCN-LCM-01 | Verify northbound creation of an app instance mapped to Nuvla deployment semantics | request/response pair, persisted resource record, logs |
| MEC-0102-02 | MEC 010-2 | `GET /app_instances` lists application instances with standards-aligned state | Scaffold only | M2 | SCN-LCM-02 | Verify query behavior, state mapping, pagination, and field selection for the selected subset | request/response pair, sample collection output, logs |
| MEC-0102-03 | MEC 010-2 | `GET /app_instances/{id}` returns one application instance | Scaffold only | M2 | SCN-LCM-03 | Verify retrieval, identifier shape, and not-found behavior | success response, not-found response, logs |
| MEC-0102-04 | MEC 010-2 | `DELETE /app_instances/{id}` removes or releases the application instance resource as defined by the selected subset | Scaffold only | M2 | SCN-LCM-04 | Verify delete semantics and cleanup behavior | delete response, final query state, logs |
| MEC-0102-05 | MEC 010-2 | `POST /app_instances/{id}/instantiate` starts lifecycle execution | Partial | M2/M3 | SCN-LCM-05 | Verify instantiate creates a real operation occurrence and triggers deployment on the selected host | request/response pair, operation record, deployment outcome, logs |
| MEC-0102-06 | MEC 010-2 | `POST /app_instances/{id}/terminate` ends lifecycle execution | Partial | M2/M3 | SCN-LCM-06 | Verify terminate creates a real operation occurrence and cleans up the instance | request/response pair, operation record, final state, logs |
| MEC-0102-07 | MEC 010-2 | `POST /app_instances/{id}/operate` supports the selected start/stop semantics | Partial / not fully wired | M3 | SCN-LCM-07 | Verify operate semantics or explicit non-support response for unsupported modes | request/response pair, state change or explicit rejection, logs |
| MEC-0102-08 | MEC 010-2 | `GET /app_lcm_op_occs` returns persisted lifecycle operations | Scaffold only | M2 | SCN-OPS-01 | Verify operation history query from real job-backed state | operation list response, job correlation, logs |
| MEC-0102-09 | MEC 010-2 | `GET /app_lcm_op_occs/{id}` returns one lifecycle operation occurrence | Scaffold only | M2 | SCN-OPS-02 | Verify operation state, timestamps, and error payloads | operation response, underlying job correlation, logs |
| MEC-0102-10 | MEC 010-2 | lifecycle state model is coherent across app instances and operations | Partial | M2 | SCN-LCM-08 | Verify state transitions and invalid transition handling | transition traces, negative test responses, logs |
| MEC-0102-11 | MEC 010-2 | ProblemDetails responses are standards-aligned for selected error cases | Partial | M2 | SCN-ERR-01 | Verify malformed, invalid-state, not-found, and unsupported-operation responses | error responses, request traces, logs |
| MEC-0102-12 | MEC 010-2 | subscriptions to lifecycle changes can be created, queried, retrieved, and deleted | Partial / in-memory only | M5 | SCN-SUB-01 | Verify durable subscription lifecycle and ownership behavior | CRUD traces, stored subscription state, logs |
| MEC-0102-13 | MEC 010-2 | lifecycle notifications are emitted to matching subscribers | Partial / dispatcher only | M5 | SCN-SUB-02 | Verify notifications for app-instance and operation-occurrence changes | webhook payloads, delivery logs, subscription match evidence |
| MEC-011-01 | MEC 011 | selected information-model fields for app, service, DNS, and traffic descriptors are represented consistently where adopted | Partial / planned | M4 | SCN-MODEL-01 | Verify adopted information-model elements are preserved from onboarding through lifecycle usage | descriptor sample, stored metadata, query output |
| MEC-037-01 | MEC 037 | valid MEC package onboarding is accepted and registered | Partial / bootstrap only | M4 | SCN-PKG-01 | Verify onboarding of a valid package and registration of metadata and version info | onboarding request/response, stored metadata, logs |
| MEC-037-02 | MEC 037 | invalid descriptor or metadata is rejected predictably | Not implemented | M4 | SCN-PKG-02 | Verify descriptor/schema validation and clean rejection behavior | invalid request, ProblemDetails response, validation logs |
| MEC-037-03 | MEC 037 | package descriptor resource is retrievable | Not implemented | M4 | SCN-PKG-03 | Verify descriptor retrieval via the exposed package sub-resource | descriptor response, content verification, logs |
| MEC-037-04 | MEC 037 | package content resource is retrievable/uploadable for the supported subset | Not implemented | M4 | SCN-PKG-04 | Verify package-content handling for the selected artifact types | upload/fetch traces, stored artifact reference, logs |
| MEC-037-05 | MEC 037 | package versioning, provenance, and checksum metadata are visible and traceable | Partial | M4 | SCN-PKG-05 | Verify metadata traceability across onboarding and later lifecycle use | metadata response, version record, checksum/provenance evidence |
| MEC-HOST-01 | MEC 003 / 010-2 | target-host selection uses explicit criteria and returns observable outcomes | Not implemented | M3 | SCN-HOST-01 | Verify supported host targeting inputs drive deterministic selection | request/response pair, selected host record, placement logs |
| MEC-HOST-02 | MEC 003 / 010-2 | host compatibility and resource checks enforce CPU, RAM, disk, architecture, and selected capabilities | Not implemented | M3 | SCN-HOST-02 | Verify admission checks for valid and invalid targets | success trace, rejection trace, placement logs |
| MEC-SEC-01 | MEC security subset | authentication and authorization are enforced on MEC-facing APIs | Partial | M5 | SCN-SEC-01 | Verify authorized access succeeds and unauthorized access is rejected | success response, 401/403 responses, auth logs |
| MEC-SEC-02 | MEC 037 security subset | package integrity or signature checks are enforced where implemented | Not implemented | M5 | SCN-SEC-02 | Verify valid artifacts pass and invalid/unsigned artifacts fail | package result, rejection result, integrity-check logs |
| MEC-OBS-01 | Validation strategy area 6 | onboarding, placement, instantiate, terminate, and failure paths are observable and auditable | Partial | M5 | SCN-OBS-01 | Verify logs, events, and operation records support evidence collection | logs, event traces, operation records, execution report notes |
| MEC-REG-01 | Validation strategy | standards layer does not regress core Nuvla deployment behavior | Not yet assessed | M5 | SCN-REG-01 | Verify existing deployment flows still behave correctly after standards-layer integration | regression test output, deployment traces, issue log |
| MEC-DEF-01 | Deferred | cancel/fail/retry operation controls remain outside MVP unless promoted | Deferred | M6 | SCN-DEF-01 | If implemented later, verify advanced operation controls; otherwise verify explicit non-claim | backlog decision, release-scope note, optional traces |
| MEC-021-01 | MEC 021 | relocation and mobility-aware orchestration | Deferred | M6 | SCN-MOB-01 | Validate only if relocation enters scope; otherwise keep out of MVP evidence set | design note, backlog decision, optional relocation traces |

## Scenario Drafts

## SCN-LCM-05: Instantiate Through Standards-Aligned Flow

Objective:

- verify that a standards-facing instantiate request results in a persisted operation occurrence and a real deployment action on the selected target

Preconditions:

- valid onboarded application package exists
- target host or placement policy resolves to an eligible MEC host
- caller has permission to operate on the application instance

Checks:

- request is accepted with the expected lifecycle response shape
- operation occurrence is persisted and queryable
- deployment reaches the expected Nuvla state
- external MEC-facing state remains coherent with internal deployment state

Evidence:

- API request/response
- operation occurrence query response
- deployment/resource snapshot
- relevant logs and event traces

## SCN-PKG-02: Reject Invalid Package or Descriptor

Objective:

- verify predictable rejection of malformed or incomplete onboarding inputs

Preconditions:

- onboarding endpoint enabled
- invalid descriptor or missing metadata prepared

Checks:

- request fails with standards-aligned error structure
- failure reason is explicit enough for validation evidence
- no partial onboarded state remains

Evidence:

- invalid request payload
- ProblemDetails response
- validation logs
- post-check confirming no invalid package was registered

## SCN-HOST-02: Reject Unsuitable Target

Objective:

- verify that incompatible hosts are rejected before deployment begins

Preconditions:

- app requirements exceed or conflict with candidate host capabilities

Checks:

- placement/admission logic rejects the request or target
- response and logs explain the mismatch
- no deployment is started on the unsuitable host

Evidence:

- placement request and response
- capability/resource snapshot for target host
- placement/admission logs
- post-check confirming no deployment action occurred

## Evidence Pack Structure

For each executed scenario, collect:

- scenario ID
- linked requirement IDs
- test setup and preconditions
- request/response traces
- operation and resource state snapshots
- logs/events/monitoring outputs
- pass/fail result
- open issues or deviations

## Recommended Maintenance Rule

Whenever a standards-facing feature changes from placeholder to real implementation, update this traceability document in the same change set as the code or test update. That will keep implementation, validation planning, and compliance claims from drifting apart again.

