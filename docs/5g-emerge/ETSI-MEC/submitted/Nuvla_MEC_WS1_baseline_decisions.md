# Nuvla MEC WS1 Baseline Decisions

## Purpose

This document records the Workstream 1 decisions that freeze the minimum viable ETSI MEC MEO scope for implementation and validation. It is the authoritative baseline for:

- what can be claimed for the MVP target
- what is explicitly deferred
- which reference-point names should be used in code and docs
- how later workstreams should interpret the selected standards subset

## Decision Summary

Workstream 1 locks the program to a minimum viable ETSI MEC MEO path centered on:

- `Mm1` northbound lifecycle and package-management APIs
- `Mm3` southbound coordination with MEC platform managers for lifecycle, capability, and resource interactions
- selected `MEC 010-2` lifecycle resources and state/error semantics
- selected `MEC 037` onboarding and package metadata behavior
- selected `MEC 011` descriptor and metadata fields only where they are adopted in the chosen onboarding subset

The MVP does **not** aim to claim full ETSI MEC coverage. It aims to claim a clear, defensible, and testable subset.

## Scope Freeze for the MVP Target

### In Scope

#### 1. MEC 003 Orchestrator Role

Claim target:

- Nuvla behaves as a recognizable MEC Orchestrator for the selected scope
- northbound orchestration is exposed through `Mm1`
- southbound host/platform coordination is exposed through `Mm3`

Interpretation:

- this is a role-level claim for the chosen subset, not a full implementation of every MEC reference point

#### 2. MEC 010-2 Lifecycle Subset

Claim target:

- `app_instances` collection and item behavior
- instantiate and terminate lifecycle operations
- `operate` only for the selected start/stop semantics if fully wired; otherwise explicit non-support must be returned
- `app_lcm_op_occs` list and get behavior
- lifecycle subscriptions and notifications
- coherent MEC state model and RFC 7807 `ProblemDetails`

Interpretation:

- cancel/fail/retry controls are not part of the MVP claim

#### 3. MEC 037 Package Management Subset

Claim target:

- package query/get/create/delete for the selected artifact model
- descriptor retrieval
- package-content retrieval/upload for the supported subset
- onboarding state, versioning, provenance, and checksum/integrity metadata

Interpretation:

- only the package formats and metadata actually supported by Nuvla should be claimed
- unsupported package forms must be rejected explicitly rather than implied as supported

#### 4. MEC 011 Adopted Information-Model Elements

Claim target:

- only the descriptor and metadata fields actually used by the onboarding and lifecycle flows

Interpretation:

- no broad claim should be made for the full MEC 011 information model
- adopted fields must remain traceable from package onboarding to lifecycle use

#### 5. Validation and Evidence Readiness

Claim target:

- the implemented subset can be validated through traceable scenarios
- evidence can be collected for onboarding, placement, instantiate, terminate, query, errors, and notifications

## Explicitly Deferred from the MVP Claim

The following items are frozen as deferred unless a later decision promotes them:

- `AppLcmOpOcc` cancel/fail/retry controls
- grant lifecycle support
- relocation and `MEC 021` mobility workflows
- CAPIF-facing integration
- native 3GPP `MnS` / `NRM` support
- full `MEC 011` information-model coverage beyond adopted fields
- `Mm5` platform configuration, traffic steering, and DNS/service-rule management as part of the MVP compliance claim
- stateful relocation or session-continuity behavior

## Reference-Point Naming Decision

### Decision

For this work package and MVP claim, the naming should be frozen as follows:

- `Mm1` = northbound OSS-to-MEO interactions
- `Mm3` = southbound MEO-to-MEPM interactions used for lifecycle, capability, resource, and host-preparation coordination
- `Mm5` = out of MVP claim scope unless a later workstream explicitly introduces platform-configuration behavior that truly belongs there

### Reason

The current codebase mixes `Mm3` and `Mm5` terminology in comments and resource descriptions. However:

- the compliance note and validation strategy are centered on `Mm1` and `Mm3`
- the current southbound MEC client under `mec/mm3_client.clj` is documented as `Mm3`
- the lifecycle and capability/resource interactions currently under discussion are part of the selected `Mm3`-oriented MVP path

### Consequence

From Workstream 1 onward:

- documentation should describe the selected southbound lifecycle/capability/resource path as `Mm3`
- legacy `Mm5` references in comments and helper descriptions should be treated as documentation debt to be normalized during related code changes
- no MVP claim should rely on `Mm5` semantics

## Lifecycle Mapping Clarification for Later Workstreams

The current Nuvla implementation model should be treated as follows during later MEC workstreams:

- `AppInstanceInfo` is expected to be exposed as a facade over Nuvla `deployment` resources
- `AppLcmOpOcc` is expected to be exposed as a facade over persisted Nuvla `job` resources
- `instantiate` should align with native deployment `start`
- selected `operate` semantics should align with native deployment `start` / `stop`
- `terminate` requires an explicit normalization decision because ETSI MEC expects a retained app instance returning to `NOT_INSTANTIATED`, while native Nuvla stop behavior naturally ends in `STOPPED`

This clarification narrows implementation choices without widening the MVP claim boundary.

## MVP Claim Boundary by Area

| Area | MVP Claim Boundary | Deferred Outside MVP |
| --- | --- | --- |
| Orchestrator role | Recognizable ETSI MEC MEO for selected `Mm1` + `Mm3` scope | Full multi-reference-point coverage |
| Lifecycle | `app_instances`, instantiate, terminate, selected `operate`, `app_lcm_op_occs`, subscriptions, ProblemDetails | cancel/fail/retry, unsupported operate modes |
| Package management | selected `MEC 037` onboarding, metadata, descriptor and package-content resources | unsupported artifact formats, broader package semantics not actually implemented |
| Information models | adopted descriptor and metadata fields only | full MEC 011 coverage |
| Southbound coordination | MEPM resolution, capability/resource checks, placement, lifecycle coordination | full platform configuration and `Mm5` service-rule management |
| 3GPP alignment | practical alignment through ETSI MEC subset | native MnS/NRM or CAPIF-first compliance |

## Required Outputs of WS1

Workstream 1 should now be considered complete only when all of the following are true:

- one frozen MVP claim boundary exists
- one frozen deferred-feature list exists
- one naming decision exists for `Mm1` / `Mm3` / `Mm5`
- the traceability draft reflects the same vocabulary and scope

## Implementation Note for Later Workstreams

Later workstreams should not widen the compliance claim silently. If a later implementation introduces a feature outside this frozen boundary, it should be handled in one of two ways:

1. mark it as implementation detail only, without changing the claim boundary
2. explicitly revise this WS1 baseline before treating it as part of the compliance scope

