# Nuvla MEC Reference-Point and Endpoint Alignment Execution Plan

## Purpose

This document translates `Nuvla_MEC_reference_point_and_endpoint_alignment_plan.md` into an execution-oriented backend backlog.

It is intended to support:

- milestone planning
- engineering assignment
- PR slicing
- implementation hand-off
- validation preparation

The execution strategy follows one delivery rule:

- land the canonical route shape and compatibility boundary first, then split package and lifecycle surfaces cleanly, then integrate the southbound `Mm3.002` / `Mm3.003` behavior, and only then rewrite the runbook and evidence trail

## Recommended Hand-Off Mode

For execution, this document should be read in milestone-first order, with workstreams aligned to the same sequence.

Practical rule:

- use `milestones` as the primary execution order
- use `RPWS` tags as the matching execution-stream labels for the same ordered steps

## Workstream Tags Used in Hand-Off

- `RPWS1` Endpoint decision lock and compatibility boundary
- `RPWS2` Canonical route layer and aliasing
- `RPWS3` Package-management tree split
- `RPWS4` Internal namespace and link normalization
- `RPWS5` MEO-produced `Mm3` package subset
- `RPWS6` `Mm3.002` southbound lifecycle command path
- `RPWS7` `Mm3.003` southbound notification ingestion
- `RPWS8` Runbook, docs, and evidence migration

## Planning Assumptions

- `ETSI GS MEC 010-2 V4.1.1` is the endpoint-shape reference
- the canonical API trees are `app_pkgm/v1` and `app_lcm/v1`
- `/mm1` and `/mm3` are allowed only as `apiRoot` prefixes if explicitly chosen
- the `Mm3` lifecycle API remains `MEPM`-produced and should not be mirrored as if Nuvla `MEO` produced it
- the current `/api/mec/app_lcm/v2/...` surface must remain available as a migration alias until the runbook and tests move
- public route changes should be sequenced before internal namespace/file renames unless the blast radius is proven small

## Decision Gates Before Coding

The execution plan assumes three explicit decisions are taken before Milestone 2 starts:

1. choose canonical public prefix style:
   - `Option A`: `/api/mec/app_pkgm/v1/...` and `/api/mec/app_lcm/v1/...`
   - `Option B`: `/api/mec/mm1/app_pkgm/v1/...`, `/api/mec/mm1/app_lcm/v1/...`, and `/api/mec/mm3/app_pkgm/v1/...`

2. choose whether `onboarded_app_packages/{appDId}` ships in the first package-route PR or in the follow-up package-completeness PR

3. choose whether file/namespace renames happen:
   - together with canonical route introduction
   - or in a dedicated normalization PR immediately after aliases are stable

## Backlog by Milestone

## Milestone 1: Endpoint Model Locked

### Goal

Freeze the route-shape choices so implementation does not oscillate during migration.

### Primary artifacts

- `submitted/Nuvla_MEC_reference_point_and_endpoint_alignment_plan.md`
- this execution plan
- updated local implementation notes if needed

### Key tasks

- `RPWS1` choose `apiRoot` style (`plain` vs `mm1/mm3` prefixes)
- `RPWS1` freeze canonical API trees:
  - `app_pkgm/v1`
  - `app_lcm/v1`
- `RPWS1` freeze URI casing decisions:
  - `appd`
  - `package_content`
- `RPWS1` freeze the `v2` compatibility policy:
  - deprecation notice
  - overlap window
  - removal trigger
- `RPWS1` freeze namespace normalization policy:
  - same PR as routing
  - or follow-up PR

### Exit criteria

- one approved public route model exists
- one approved alias/deprecation policy exists
- one approved namespace-normalization policy exists

## Milestone 2: Canonical Public Routes Introduced

### Goal

Add the new standards-aligned route layer without breaking the current demo surface.

### Primary code touchpoints

- `api-server/code/src/com/sixsq/nuvla/server/resources/mec.clj`
- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/app_lcm_v2.clj`
- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/app_package.clj`
- any route registration or OpenAPI wiring that advertises the current paths

### Key tasks

- `RPWS2` introduce canonical package routes under `app_pkgm/v1`
- `RPWS2` introduce canonical lifecycle routes under `app_lcm/v1`
- `RPWS2` keep `/api/mec/app_lcm/v2/...` as compatibility aliases
- `RPWS2` ensure both route sets point at the same implementation where semantics truly match
- `RPWS2` update `Location` headers to prefer canonical routes
- `RPWS2` update response `_links` generation to prefer canonical routes

### Test touchpoints

- `api-server/code/test/com/sixsq/nuvla/server/resources/mec/app_lcm_v2_test.clj`
- `api-server/code/test/com/sixsq/nuvla/server/resources/mec/app_package_test.clj`
- `api-server/code/test/com/sixsq/nuvla/server/resources/mec/app_package_lifecycle_test.clj`
- `api-server/code/test/com/sixsq/nuvla/server/resources/mec/app_lcm_subscription_test.clj`

### Exit criteria

- canonical routes return the same behavior as the old routes for the supported subset
- old `v2` routes still pass compatibility tests
- generated links no longer point at canonical-invalid shapes

## Milestone 3: Package Tree Split Completed

### Goal

Separate package management from lifecycle management according to the spec URI structure.

### Primary code touchpoints

- `api-server/code/src/com/sixsq/nuvla/server/resources/mec.clj`
- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/app_package.clj`
- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/query_filter.clj`
- any doc or helper that still describes package resources under `app_lcm`

### Key tasks

- `RPWS3` move package collection/item behavior under `app_pkgm/v1`
- `RPWS3` rename `/appD` route to `/appd`
- `RPWS3` ensure `package_content` is fully attached to the package tree
- `RPWS3` decide and implement `onboarded_app_packages/{appDId}`:
  - collection alias
  - individual resource alias
  - `appd`
  - `package_content`
- `RPWS3` add or update package subscriptions under the package tree if in scope for the selected increment

### Test touchpoints

- `api-server/code/test/com/sixsq/nuvla/server/resources/mec/app_package_test.clj`
- `api-server/code/test/com/sixsq/nuvla/server/resources/mec/app_package_lifecycle_test.clj`

### Exit criteria

- no canonical package route lives under `app_lcm`
- package links use `app_pkgm/v1`
- `appd` route casing matches the spec
- package route coverage exists for both positive and negative cases

## Milestone 4: Internal Name and Link Normalization

### Goal

Remove misleading internal names after the public route surface is stable.

### Primary code touchpoints

- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/app_lcm_v2.clj`
- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/app_instance.clj`
- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/app_lcm_op_occ.clj`
- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/app_lcm_subscription.clj`
- imports across MEC namespaces and tests

### Key tasks

- `RPWS4` rename `com.sixsq.nuvla.server.resources.mec.app-lcm-v2`
- `RPWS4` rename `app_lcm_v2.clj`
- `RPWS4` remove hardcoded `/app_lcm/v2/...` links from helper namespaces
- `RPWS4` review other namespaces that still encode obsolete route/version choices

### Exit criteria

- no primary namespace/file still encodes `v2` as the intended API shape
- no generated link points at `app_lcm/v2` except explicit compatibility paths

## Milestone 5: MEO-Produced Mm3 Package Subset

### Goal

Add the `Mm3` package-management subset without confusing it with the `Mm3` lifecycle API.

### Primary code touchpoints

- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/app_package.clj`
- route adapter layer in `mec.clj` or new dedicated route namespace
- package-notification helpers to be added or extended

### Key tasks

- `RPWS5` expose query/get/fetch behavior for MEPM consumers using the canonical package tree
- `RPWS5` if explicit reference-point prefixes are chosen, add `/mm3/app_pkgm/v1/...` package routes
- `RPWS5` add package-management subscription resources if they are part of the selected compliance increment
- `RPWS5` add package event emission for:
  - onboarding
  - package state changes
- `RPWS5` ensure access-control and audit labels distinguish `Mm1` and `Mm3` callers where needed

### Test touchpoints

- package lifecycle tests
- package subscription tests to be added if not already present

### Exit criteria

- Table 8 no longer depends only on northbound package handlers
- package-management subscription behavior is demonstrable if claimed

## Milestone 6: Mm3.002 Southbound Command Integration

### Goal

Make northbound MEC lifecycle actions consistently drive the external `MEPM` through `Mm3.002`.

### Primary code touchpoints

- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/app_lcm_v2.clj`
- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/mm3_client.clj`
- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/lifecycle_handler.clj`
- `job-engine/nuvla/job_engine/job/actions/utils/mm3_client.py`
- deployment/job action code paths that already carry MEC metadata
- `api-server/code/test/com/sixsq/nuvla/server/resources/mec/mock_mepm_server.clj`

### Key tasks

- `RPWS6` stabilize southbound `appInstanceId` persistence
- `RPWS6` persist southbound operation identifiers where available
- `RPWS6` make instantiate / terminate / operate share a consistent command path
- `RPWS6` query southbound operation state when reconciliation is needed
- `RPWS6` extend the mock `MEPM` so the local environment exposes the `Mm3` lifecycle resources needed for the selected subset
- `RPWS6` ensure northbound `AppLcmOpOcc` is derived from persisted job state plus southbound correlation
- `RPWS6` preserve correct terminate normalization toward `NOT_INSTANTIATED`

### Test touchpoints

- `api-server/code/test/com/sixsq/nuvla/server/resources/mec/app_lcm_v2_test.clj`
- `api-server/code/test/com/sixsq/nuvla/server/resources/mec/lifecycle_handler_test.clj`
- `api-server/code/test/com/sixsq/nuvla/server/resources/mec/integration_test.clj`
- job-engine MEC action tests

### Exit criteria

- northbound lifecycle calls always produce stable southbound correlation fields
- failures are surfaced as standards-aligned northbound errors
- mock `MEPM` logs prove the `Mm3.002` path

## Milestone 7: Mm3.003 Southbound Event Ingestion

### Goal

Use `Mm3.003` notifications to reconcile Nuvla lifecycle truth and trigger northbound notifications.

### Primary code touchpoints

- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/notification_dispatcher.clj`
- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/app_lcm_subscription.clj`
- new internal callback receiver namespace
- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/app_lcm_op_occ.clj`
- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/job_notifications.clj`
- `api-server/code/src/com/sixsq/nuvla/server/resources/mepm.clj`
- `api-server/code/test/com/sixsq/nuvla/server/resources/mec/mock_mepm_server.clj`

### Key tasks

- `RPWS7` create one southbound subscription per `MEPM`
- `RPWS7` persist southbound subscription ids on the `mepm` resource
- `RPWS7` add internal callback route for `Mm3.003`
- `RPWS7` correlate inbound events to `deployment` and `job`
- `RPWS7` update internal state idempotently on repeated notifications
- `RPWS7` trigger existing northbound webhook delivery after reconciliation
- `RPWS7` extend the mock `MEPM` so it can emit representative `Mm3.003` lifecycle notifications during local validation

### Test touchpoints

- `api-server/code/test/com/sixsq/nuvla/server/resources/mec/notification_dispatcher_test.clj`
- `api-server/code/test/com/sixsq/nuvla/server/resources/mec/subscription_lifecycle_test.clj`
- new callback-receiver tests
- integration tests with mock `MEPM` notifications

### Exit criteria

- a southbound lifecycle event can complete or fail the corresponding Nuvla job/op-occ
- northbound lifecycle notifications can be shown to derive from reconciled southbound state
- callback traces are available for evidence

## Milestone 8: Runbook, Docs, and Validation Migration

### Goal

Move the user-visible story to the canonical routes and newly implemented flows.

Implementation note:

- the concrete public roots are `/api/mec/mm1/app_pkgm/v1` and `/api/mec/mm1/app_lcm/v1`
- the concrete internal southbound callback receiver is `/api/mec/internal/mm3/app_lcm/v1/notifications`

### Primary artifact touchpoints

- `api-server/docs/5g-emerge/ETSI-MEC/submitted/Nuvla_MEC_local_demo_runbook.md`
- `api-server/docs/5g-emerge/ETSI-MEC/submitted/Nuvla_MEC_reference_point_and_endpoint_alignment_plan.md`
- `api-server/docs/5g-emerge/ETSI-MEC/submitted/5G-EMERGE_P2_TN_2.3.2 Compliance_of_the_orchestration_software v06-pre-release.md`
- slides / supporting docs that still hardcode `/app_lcm/v2`
- validation notes that should reference the new evidence paths

### Key tasks

- `RPWS8` rewrite runbook examples to canonical routes
- `RPWS8` if explicit prefixes were chosen, use them consistently in every example
- `RPWS8` add package-tree exercises under `app_pkgm/v1`
- `RPWS8` add evidence capture for `Mm3.002`
- `RPWS8` add `Mm3.003` callback exercise if implemented
- `RPWS8` remove stale references to `app_lcm/v2` from public docs

### Exit criteria

- the runbook uses only canonical or explicitly marked compatibility routes
- docs and runbook match the actual router behavior
- validation evidence can be gathered from the documented flow
- the docs call out the persisted `Mm3.002` / `Mm3.003` evidence fields and callback route explicitly

## Suggested PR Slices

### PR 1: Decision lock and canonical route shell

Scope:

- freeze chosen `apiRoot` style in docs
- add canonical route roots
- keep `v2` aliases
- update `_links` and `Location` headers where easy

### PR 2: Package tree split

Scope:

- move package routes to `app_pkgm/v1`
- rename `/appD` to `/appd`
- update package tests

### PR 3: Namespace and hardcoded-link normalization

Scope:

- rename `app-lcm-v2` namespace/file
- remove remaining hardcoded `/app_lcm/v2/...` links

### PR 4: Mm3 package subset

Scope:

- package query/fetch/subscription behavior for MEPM-facing use
- package event types if in scope

### PR 5: Mm3.002 hardening

Scope:

- stabilize southbound identifiers and correlation
- improve job/op-occ derivation and failure handling

### PR 6: Mm3.003 callback integration

Scope:

- southbound subscription management
- callback receiver
- reconciliation to deployment/job state
- northbound notification fan-out

### PR 7: Runbook and evidence migration

Scope:

- final route migration in docs
- new exercises and evidence steps

## Minimal First Increment

If the team wants the smallest credible first increment, do this first:

1. Milestone 1 decision lock
2. Milestone 2 canonical routes plus aliases
3. Milestone 3 package split
4. Milestone 4 namespace normalization
5. partial Milestone 8 runbook migration

That already delivers:

- spec-shaped public routes
- a cleaner package/lifecycle split
- a stable migration story

without blocking on the full `Mm3.003` event-ingestion design.

## Final Delivery Rule

Implementation should not mix these three changes blindly in one step:

- public route migration
- namespace/file renaming
- southbound behavioral changes

They are related, but they have different risk profiles. The safest sequence is:

1. route shape
2. internal naming
3. southbound behavior
4. docs and evidence
