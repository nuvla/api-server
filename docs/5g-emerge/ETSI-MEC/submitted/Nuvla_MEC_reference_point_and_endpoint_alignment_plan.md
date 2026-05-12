# Nuvla MEC Reference-Point and Endpoint Alignment Plan

## Purpose

This document defines a concrete plan to align Nuvla's MEC-facing APIs, southbound integration, and demo documentation with the current ETSI GS MEC 010-2 interpretation, using `ETSI GS MEC 010-2 V4.1.1 (2025-05)` as the clearest reference point.

It focuses on four related problems:

- the current Nuvla API mixes package and lifecycle resources under `app_lcm/v2`
- the current Nuvla API does not expose a stable, spec-defensible URI strategy for reference points, package-management roots, and lifecycle roots
- the southbound `Mm3.002` / `Mm3.003` interaction model is only partially reflected in Nuvla today
- the local demo runbook still exercises legacy paths rather than the spec-aligned endpoint structure

## Executive Summary

The main alignment decisions should be:

1. treat `ETSI GS MEC 010-2 V4.1.1` as the current endpoint-structure reference
2. use the spec-native API names and versions while allowing `/mm1` and `/mm3` as `apiRoot` prefixes where helpful
3. use the spec-native API names and versions:
   - `app_pkgm/v1`
   - `app_lcm/v1`
4. separate route namespaces from implementation reuse:
   - shared internal service code is good
   - blindly exposing the same handler under both `Mm1` and `Mm3` is not always correct
5. treat `Mm3.002` as southbound command execution and `Mm3.003` as southbound event ingestion
6. finish by updating the demo runbook to use the corrected endpoints and to exercise the newly aligned flows

## Standards Baseline

### What V4.1.1 says

The relevant `Mm3` split is explicit in `V4.1.1`:

- `Mm3.001`: application package management interface produced by the `MEC Orchestrator`
- `Mm3.002`: application lifecycle management interface produced by the `MEC Platform Manager`
- `Mm3.003`: application lifecycle change notification interface produced by the `MEC Platform Manager`

The top-level URI root is also explicit:

- `{apiRoot}/{apiName}/{apiVersion}/`
- `apiName = app_pkgm` for package management
- `apiName = app_lcm` for lifecycle management
- `apiVersion = v1`

So the standards-grounded canonical shapes are:

- `.../app_pkgm/v1/...`
- `.../app_lcm/v1/...`

not `.../app_lcm/v2/...`.

### What this means for Nuvla

- `Mm1` is northbound and produced by Nuvla in its `MEO` role
- `Mm3` package management is also produced by Nuvla when acting as `MEO`
- `Mm3` lifecycle management is produced by the external `MEPM`, not by Nuvla in its `MEO` role

That means a clean endpoint plan cannot simply mirror every resource under both `Mm1` and `Mm3` on the same Nuvla API surface.

## Current State in Nuvla

### Current northbound path

Today Nuvla exposes MEC resources under:

- `/api/mec/app_lcm/v2/app_packages`
- `/api/mec/app_lcm/v2/app_instances`
- `/api/mec/app_lcm/v2/app_lcm_op_occs`
- `/api/mec/app_lcm/v2/subscriptions`

This creates three mismatches with the spec:

1. package resources are under `app_lcm` instead of `app_pkgm`
2. the current version is `v2` instead of `v1`
3. package-management and lifecycle resources are not separated according to the spec URI roots

### Likely origin of `v2`

The current `v2` appears to be a Nuvla-local implementation choice that was propagated through:

- `mec.clj`
- `app_lcm_v2.clj`
- `app_package.clj`
- the local `mec-010-2-openapi.yaml`
- the runbook and supporting docs

It does not appear to come from the ETSI `010-2` API root definitions. The current ETSI text still says `app_pkgm/v1` and `app_lcm/v1`.

## Endpoint Alignment Decisions

## Decision 1: Use explicit reference-point public URI roots

The canonical public routes should use the explicit prefixes implemented in Nuvla:

- `/api/mec/mm1/app_pkgm/v1/...`
- `/api/mec/mm1/app_lcm/v1/...`
- `/api/mec/mm3/app_pkgm/v1/...`

The figures in `V4.1.1` show that the same URI trees are used on the relevant reference points:

- `app_pkgm/v1` for application package management
- `app_lcm/v1` for application lifecycle management

The standard says that `apiRoot` contains an optional prefix string, so `/mm1` and `/mm3` can be encoded there consistently without changing the ETSI relative resource trees. This is the option chosen for Nuvla because it makes the producer-consumer role explicit in links, examples, and evidence capture.

For `Mm3`, only the interfaces actually produced by Nuvla in the `MEO` role should be exposed by the Nuvla API server:

- `/api/mec/mm3/app_pkgm/v1/...` when serving the MEO-produced package-management subset to a MEPM consumer

The `Mm3` lifecycle API should not be presented as a normal public `MEO` endpoint on the Nuvla API server, because under the standard it is produced by the `MEPM`.

So this should **not** be the main design target for the Nuvla API server:

- `/api/mec/app_lcm/v1/...` exposed by Nuvla and described as if it were the `MEPM`-produced `Mm3` lifecycle API
- `/api/mec/mm3/app_lcm/v1/...` exposed by Nuvla and described as if it were the `MEPM`-produced `Mm3` lifecycle API

That path only makes sense on:

- the mock `MEPM`
- a real `MEPM`
- or a dedicated Nuvla-hosted mock/adapter that is explicitly acting as a `MEPM`

## Decision 2: Use spec-native API names

The canonical names should be:

- package management: `app_pkgm`
- lifecycle management: `app_lcm`

That implies:

- move `app_packages` under `.../app_pkgm/v1/...`
- keep `app_instances`, `app_lcm_op_occs`, and lifecycle subscriptions under `.../app_lcm/v1/...`

## Decision 3: Use spec-native `v1`

The canonical public version should be `v1`, because that is what `ETSI GS MEC 010-2 V4.1.1` defines for the API root.

Migration strategy:

- introduce `v1` canonical routes first
- keep `v2` as a deprecated compatibility alias for a transition period
- update docs and runbook to use `v1`
- remove `v2` only after internal consumers and demos have moved

## Decision 4: Normalize resource names and casing

Where possible, align with ETSI naming:

- use `appd` in the URI, not `appD`
- use `app_pkgm/v1/.../appd`
- keep `package_content` as in the spec

Internally, code can still use `AppD` as a type name or field name, but the route shape should match the spec.

## Decision 5: Normalize misleading internal namespace and file names

The plan should explicitly include internal renaming where names encode obsolete API-shape decisions or misleading scope.

This is not required for immediate wire compatibility, but it is worth planning because otherwise the codebase will keep
advertising outdated semantics even after the routes are corrected.

### High-priority examples

- `com.sixsq.nuvla.server.resources.mec.app-lcm-v2`
  - should be renamed once the canonical route layer is moved to `app_lcm/v1`
- `app_lcm_v2.clj`
  - should be renamed consistently with the namespace

### Other candidates to review

- namespaces and files that still encode temporary route/version choices
- helpers whose names imply the wrong reference-point producer role
- route modules that still describe package resources as part of `app_lcm`

### Practical rule

- rename public routes first
- then rename internal namespaces/files in a second pass or the same change set if the blast radius is manageable
- keep compatibility thin where needed, but do not keep misleading names indefinitely

## Shared Code vs Separate Endpoints

## Principle

Shared code behind multiple reference-point endpoints makes sense only at the service layer, not necessarily at the route/producer layer.

### Good to share

- payload validation
- resource-to-DTO mapping
- filtering logic
- `ProblemDetails` helpers
- package metadata normalization
- lifecycle state mapping
- notification serialization helpers

### Should stay separate

- route namespaces
- authentication and authorization policy
- request ownership and caller role
- correlation rules
- producer/consumer semantics
- audit and trace labeling

### Practical rule

When two endpoints are truly the same operation exposed by the same producer with the same semantics, they may share the same underlying handler.

When the same conceptual resource appears on two reference points but is produced by different actors, use:

- a shared domain/service layer
- separate thin route adapters

### Example

This is a good idea:

- one shared package-query service
- one `Mm1` route adapter for OSS clients
- one `Mm3` route adapter for MEPM clients

This is **not** a good idea:

- exposing the exact same Nuvla `MEO` lifecycle handler as both the normal northbound lifecycle API and the `MEPM`-produced `Mm3` lifecycle API

because on `Mm3` lifecycle the producer is the `MEPM`, not the `MEO`.

## Recommended Canonical Endpoint Map

| Area | Reference point | Producer | Canonical path root | Notes |
| --- | --- | --- | --- | --- |
| Package management | `Mm1` | `MEO` | `/api/mec/mm1/app_pkgm/v1` | Main northbound package API |
| Lifecycle management | `Mm1` | `MEO` | `/api/mec/mm1/app_lcm/v1` | Main northbound lifecycle API |
| Package management subset | `Mm3` | `MEO` | `/api/mec/mm3/app_pkgm/v1` | Same API tree, different producer-consumer context |
| Lifecycle management | `Mm3` | `MEPM` | external `MEPM`, not Nuvla MEO root | Consumed by Nuvla through `mm3_client` |
| Lifecycle change notification | `Mm3` | `MEPM` | `/api/mec/internal/mm3/app_lcm/v1/notifications` | Internal callback receiver for southbound events |

## Mm3.002 Integration Plan

## Goal

Integrate `Mm3.002` as the southbound lifecycle execution path behind Nuvla's northbound MEC lifecycle facade.

## When Nuvla should call `Mm3.002`

Nuvla should invoke `Mm3.002` endpoints when a northbound `Mm1` lifecycle request has been accepted and translated into a deployment/job-backed action:

- instantiate
- terminate
- operate
- optionally query app-instance details or operation status when reconciliation is needed

## Recommended flow

1. client calls `Mm1` lifecycle endpoint under `/api/mec/mm1/app_lcm/v1/...`
2. Nuvla validates request and resolves target `MEPM`
3. Nuvla creates or tags the corresponding `deployment` and `job`
4. the job executor calls the external `MEPM` lifecycle endpoint through the `Mm3` client
5. Nuvla stores:
   - southbound `appInstanceId`
   - southbound lifecycle operation id if returned
   - target `mepm-id`
   - target endpoint
6. Nuvla exposes the resulting state through northbound `AppInstanceInfo` and `AppLcmOpOcc`

## Required implementation tasks

- stabilize the mapping between `deployment` and southbound `appInstanceId`
- stabilize the mapping between `job` and southbound lifecycle operation id
- make southbound operation status queryable for reconciliation and failure handling
- normalize `terminate` so the northbound retained app instance returns to `NOT_INSTANTIATED`

## Mm3.003 Integration Plan

## Goal

Integrate `Mm3.003` as the southbound event source that updates Nuvla's lifecycle truth and drives northbound notifications.

## When Nuvla should subscribe

Nuvla should subscribe once per `MEPM`, not once per user request.

Preferred model:

- create or ensure a southbound `Mm3.003` subscription when an `MEPM` resource is created, enabled, or first used
- store the resulting southbound subscription identifier on the `mepm` resource
- renew or recreate it when needed

## Callback endpoint

Nuvla should expose an internal callback endpoint dedicated to southbound `Mm3.003` notifications, for example:

- `/api/mec/internal/mm3/app_lcm/v1/notifications`

This should be treated as an internal producer-consumer contract between `MEPM` and `Nuvla`, not as a user-facing standards endpoint.

## What Nuvla should do when notifications arrive

For each southbound lifecycle notification:

1. correlate it to Nuvla state
   - match southbound `appInstanceId` to `deployment`
   - match southbound operation id to `job` / `AppLcmOpOcc`
   - verify `mepm-id`
2. persist the update
   - deployment operational / instantiation state
   - job / op-occ state
   - timestamps
   - error details
   - raw notification payload if useful for evidence
3. reconcile async execution
   - complete or fail the corresponding job
   - deduplicate repeated notifications
4. emit northbound notifications
   - trigger the existing lifecycle webhook dispatch for Nuvla-facing subscriptions

## Resulting architectural model

- `Mm1` northbound call starts the operation
- `Mm3.002` performs the southbound command
- `Mm3.003` confirms progress and completion
- Nuvla translates southbound state into northbound MEC resources and notifications

## Workstreams

## Workstream 1: Freeze the canonical endpoint model

Scope:

- approve `V4.1.1` as the API-shape reference
- approve spec-native `app_pkgm/v1` and `app_lcm/v1` as the canonical API trees
- decide whether to use plain `/api/mec/...` paths or explicit `/api/mec/mm1/...` and `/api/mec/mm3/...` prefixes as the chosen `apiRoot` style
- approve `app_pkgm/v1` and `app_lcm/v1`
- approve lower-case `appd`
- decide deprecation behavior for legacy `app_lcm/v2`

Outputs:

- endpoint decision record
- canonical path matrix
- deprecation note for `v2`

## Workstream 2: Introduce canonical public routes

Scope:

- add `/api/mec/mm1/app_pkgm/v1/...` and `/api/mec/mm1/app_lcm/v1/...`
- keep current `v2` routes as aliases during migration
- update generated links and `Location` headers to prefer canonical routes
- define the follow-up rename set for files/namespaces that still encode `v2`

Outputs:

- canonical northbound route layer
- compatibility aliases

## Workstream 3: Separate package management from lifecycle management

Scope:

- move package handlers out of the `app_lcm` route tree
- expose them under `app_pkgm/v1`
- rename `/appD` to `/appd`
- optionally add the `onboarded_app_packages/{appDId}` tree where selected

Outputs:

- spec-shaped package API
- corrected package hyperlinks

## Workstream 4: Add `Mm3` package-management subset

Scope:

- expose MEO-produced package query/get/fetch/subscription endpoints with the canonical `app_pkgm/v1` URI shape and document the `Mm3` producer-consumer context for MEPM consumers
- if explicit reference-point prefixes are chosen, use `/api/mec/mm3/app_pkgm/v1/...` for this subset
- reuse shared package services behind separate `Mm3` route adapters
- add package-management notifications:
  - `AppPackageOnBoardingNotification`
  - `AppPackageStateChangeNotification`

Outputs:

- materially improved Table 8 alignment
- distinct `Mm3` package surface

## Workstream 5: Integrate `Mm3.002`

Scope:

- make `Mm1` lifecycle actions consistently invoke the external `MEPM` through the `Mm3` client
- persist southbound identifiers and correlations
- expose reliable northbound operation state derived from real job state plus southbound correlation
- extend the mock `MEPM` so the selected `Mm3` lifecycle API subset can be exercised locally

Outputs:

- auditable southbound lifecycle command path
- improved `Mm3.002` alignment

## Workstream 6: Integrate `Mm3.003`

Scope:

- create one southbound subscription per `MEPM`
- add internal callback receiver for `Mm3.003`
- correlate inbound events to `deployment` and `job`
- drive northbound lifecycle notification dispatch from reconciled internal state
- extend the mock `MEPM` so it can emit representative `Mm3.003` events in the local demo and integration tests

Outputs:

- event-driven lifecycle reconciliation
- improved `Mm3.003` alignment
- cleaner evidence for validation

## Workstream 7: Migrate docs and runbook

Scope:

- update all public docs to canonical spec-shaped public paths
- correct package paths to `app_pkgm/v1`
- replace `app_lcm/v2` examples with canonical routes
- document that `Mm3` lifecycle is an external `MEPM` contract, not a user-facing alias on the Nuvla `MEO` API
- if explicit prefixes are chosen, explain them as part of `apiRoot` rather than as part of `apiName` or `apiVersion`

Outputs:

- aligned docs and examples
- reduced naming confusion

## Runbook Update and Exercise Plan

The final step should update `Nuvla_MEC_local_demo_runbook.md` to use the canonical routes and to exercise the newly aligned parts.

### Runbook route migration

Update examples from:

- `/api/mec/app_lcm/v2/app_packages`
- `/api/mec/app_lcm/v2/app_instances`
- `/api/mec/app_lcm/v2/subscriptions`

to the explicit prefixes implemented in Nuvla:

- `/api/mec/mm1/app_pkgm/v1/app_packages`
- `/api/mec/mm1/app_pkgm/v1/app_packages/{appPkgId}/appd`
- `/api/mec/mm1/app_pkgm/v1/app_packages/{appPkgId}/package_content`
- `/api/mec/mm1/app_lcm/v1/app_instances`
- `/api/mec/mm1/app_lcm/v1/app_lcm_op_occs`
- `/api/mec/mm1/app_lcm/v1/subscriptions`
- `/api/mec/internal/mm3/app_lcm/v1/notifications` for the internal southbound callback receiver

### New scenarios to add to the runbook

1. package onboarding through the canonical package endpoints
2. package descriptor retrieval through `app_pkgm/v1/.../appd`
3. package content fetch/upload through `app_pkgm/v1/.../package_content`
4. lifecycle create / instantiate / operate / terminate through canonical lifecycle endpoints
5. explicit evidence of southbound `Mm3.002` calls in the mock `MEPM`
6. if implemented in time, a package-management subscription scenario for the `Mm3` package subset
7. if implemented in time, a southbound `Mm3.003` notification scenario:
   - `MEPM` sends lifecycle event
   - Nuvla reconciles deployment/job state
   - Nuvla emits matching northbound webhook notification

### Evidence to collect

- canonical request and response traces on `Mm1`
- mock `MEPM` logs for southbound lifecycle commands
- `AppLcmOpOcc.mepmOperationId` values to correlate northbound and southbound lifecycle operations
- southbound callback traces for `Mm3.003`
- persisted `mepm.mm3-last-notification`, `deployment.mec-last-notification`, and `job.mec-last-notification` snapshots
- northbound webhook notifications emitted by Nuvla
- final app instance and operation occurrence state queries

## Namespace Normalization Candidates

The following names should be reviewed as part of the migration:

- `com.sixsq.nuvla.server.resources.mec.app-lcm-v2`
- `api-server/code/src/com/sixsq/nuvla/server/resources/mec/app_lcm_v2.clj`
- any route/link builders still hardcoding `/app_lcm/v2/...`
- any package-management modules whose naming still suggests they belong under the lifecycle tree

## Recommended Order

If the goal is the fastest path to a credible improvement in Annex A alignment, implement in this order:

1. freeze endpoint and versioning decisions
2. introduce canonical public routes and `v2` compatibility aliases
3. split package resources into `app_pkgm/v1`
4. add package-management subscriptions/notifications for the `Mm3` package subset
5. complete `Mm3.002` lifecycle-command integration
6. add `Mm3.003` subscription and callback handling
7. update the runbook and validation evidence

## Final Decision Summary

The plan should assume the following end state:

- canonical API trees:
  - `mm1/app_pkgm/v1`
  - `mm1/app_lcm/v1`
  - internal callback receiver at `internal/mm3/app_lcm/v1/notifications`
- chosen `apiRoot` style:
  - explicit `/api/mec/mm1/...` and `/api/mec/mm3/...` prefixes
- optional MEO-produced `Mm3` package subset reusing the canonical `app_pkgm/v1` tree in the appropriate producer-consumer context
- no misleading `Mm3` lifecycle alias on the Nuvla `MEO` API root
- southbound `Mm3.002` consumed through the external `MEPM`
- southbound `Mm3.003` consumed through an internal callback receiver and used to reconcile Nuvla state
- southbound command and callback evidence persisted on the correlated Nuvla resources
- legacy `/api/mec/app_lcm/v2/...` routes treated as migration debt, not as the standards reference shape
