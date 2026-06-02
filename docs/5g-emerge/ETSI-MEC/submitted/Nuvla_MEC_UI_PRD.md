# Nuvla ETSI MEC UI Product Requirements Document

## Purpose

This document defines the UI work needed to expose the ETSI MEC functionality already implemented server-side in Nuvla, while keeping the existing user experience unchanged for users who are not interested in MEC.

The core product constraint is:

- all ETSI MEC UI must be hidden behind a feature flag

This PRD focuses on the user-facing UI behavior, navigation, information architecture, rollout boundaries, and acceptance criteria for the MEC UI layer.

## Product Principle

ETSI MEC must feel like an optional advanced capability, not a new mandatory mental model for the whole product.

Therefore:

- users without the ETSI MEC feature flag must not see MEC-specific pages, tabs, actions, labels, or subtype choices
- users with the ETSI MEC feature flag enabled should see a coherent MEC workflow inside the existing Nuvla Apps and Deployments experience
- MEC should reuse existing Nuvla concepts where possible and only introduce MEC-native terms where they are necessary for standards-aligned behavior

## Problem Statement

The server-side implementation now exposes meaningful ETSI MEC functionality:

- MEC application package and AppD onboarding semantics
- MEC application lifecycle APIs
- MEC lifecycle operation tracking
- MEPM inventory and health/capability/resource actions
- MEC subscriptions and notifications

However, the current UI does not expose these capabilities in a usable way. As a result:

- ETSI MEC workflows are effectively API-only
- operators cannot easily inspect MEC-specific state from the UI
- MEC application modules are not first-class in the UI
- the implementation is difficult to demo, validate, and operate without direct API usage

## Goals

- provide a usable ETSI MEC UI for enabled users
- keep the default Nuvla UI unchanged for non-enabled users
- integrate MEC into existing Apps and Deployments flows rather than creating a disconnected parallel product
- expose enough MEC state and actions to support demo, operator, and validation needs
- stage the work so that the first visible MEC UI increment delivers value early

## Non-Goals

- exposing MEC concepts to all users by default
- redesigning the full Nuvla navigation model
- implementing all possible ETSI MEC standards features in one UI release
- replacing existing generic deployment workflows for non-MEC applications
- building a full external developer portal for raw MEC API exploration

## Target Users

### Primary users

- Nuvla operators working on ETSI MEC and 5G-EMERGE scenarios
- technical validators and demo operators who need to show end-to-end MEC behavior
- advanced users managing MEPM-backed MEC application lifecycle flows

### Secondary users

- internal developers and testers validating the northbound and southbound MEC integration from the UI

### Excluded users by default

- standard Nuvla users not working with ETSI MEC

## Feature Flag Requirement

The ETSI MEC UI must be protected by a single feature flag, for example:

- `etsi-mec`

The feature flag is the product boundary for this PRD.

When the flag is disabled:

- no MEC navigation item is visible
- no MEC app subtype is visible in app creation flows
- no MEC-specific tabs, labels, or actions are visible in app or deployment details
- no MEC-specific administration pages are reachable through normal UI flows

When the flag is enabled:

- all MEC UI described in this PRD becomes visible
- the MEC experience must feel internally consistent across navigation, details, and actions

For this PRD, a UI feature flag is sufficient. A broader entitlement or server-driven capability model is explicitly out of scope.

## UX Strategy

The ETSI MEC UI should be introduced in three layers:

1. hidden capability plumbing
2. embedded MEC support inside existing Apps and Deployments flows
3. dedicated MEC operational views where generic Nuvla views are not sufficient

This preserves the familiar Nuvla structure while still making MEC operationally usable.

## User Outcomes

With ETSI MEC enabled, a user should be able to:

- create and manage a MEC application as a first-class app type
- understand whether a deployment is a generic deployment or a MEC-backed app instance
- inspect MEC lifecycle state without using raw APIs
- trigger and monitor MEC lifecycle actions
- inspect MEPM status, capabilities, and resources
- manage MEC notification subscriptions

With ETSI MEC disabled, a user should experience:

- no additional clutter
- no new terminology
- no visual regressions or ambiguity in existing flows

## Scope

## Workstream 1: Feature Flag Foundation

### Objective

Create the visibility boundary that keeps ETSI MEC hidden unless explicitly enabled.

### Requirements

- add a new ETSI MEC feature flag to the existing UI feature-flag system
- use the same flag consistently across sidebar items, routes, subtype options, tabs, and action buttons
- ensure disabled-state users do not encounter broken links, blank tabs, or MEC placeholders

### Acceptance criteria

- a user with the flag disabled cannot discover MEC UI through normal navigation
- a user with the flag enabled can access the MEC UI surfaces defined in this PRD
- toggling the feature flag cleanly adds or removes MEC UI affordances without side effects on non-MEC features

## Workstream 2: MEC Application Support in Apps

### Objective

Make `application_mec` a first-class app subtype in the UI.

### Requirements

- add an ETSI MEC application subtype to app creation and detail flows
- display a clear subtype label, such as `ETSI MEC`
- provide an AppD-oriented editing and viewing experience
- show MEC-relevant app metadata in overview and details views

### Minimum UI requirements

- new subtype option in the Add Application flow, only when the feature flag is enabled
- app details support for the MEC subtype without fallback or broken rendering
- overview fields for:
  - AppD identifier
  - MEC version
  - app provider
  - required MEC services
  - resource requirements
- MEC-specific configuration sections for:
  - software images
  - external connection points
  - traffic rules
  - DNS rules

### UX guidance

- reuse the existing app detail structure and tabs where possible
- do not expose raw JSON by default if a structured view is feasible
- allow advanced raw AppD inspection only as a secondary affordance if needed

### Acceptance criteria

- enabled users can create and edit a MEC app without UI errors
- existing Docker, Kubernetes, Helm, and bouquet flows remain unchanged
- disabled users do not see the MEC subtype option

## Workstream 3: MEC-Aware Deployment Details

### Objective

Expose MEC lifecycle semantics inside deployment details.

### Requirements

- identify deployments that represent MEC app instances
- show MEC-specific state in deployment details
- make MEC lifecycle transitions understandable without requiring API inspection

### Minimum UI requirements

- show MEC status fields when applicable:
  - app instance id
  - instantiation state
  - operational state
  - target MEC host
  - selected MEPM
- expose lifecycle actions when appropriate:
  - instantiate
  - terminate
  - operate start
  - operate stop
- show lifecycle action availability only when it matches valid MEC state transitions

### UX guidance

- generic deployment actions should remain intact for non-MEC deployments
- MEC actions should not appear on deployments that are not part of the MEC workflow
- MEC state should be visible in Overview before users open deeper tabs

### Acceptance criteria

- enabled users can distinguish a MEC-backed deployment from a generic deployment
- enabled users can see the current MEC lifecycle state in deployment details
- disabled users see no MEC-specific deployment fields or buttons

## Workstream 4: Lifecycle Operation Visibility

### Objective

Make asynchronous MEC lifecycle operations observable in the UI.

### Requirements

- expose operation occurrences in a user-readable way
- make operation progress and failure reasons visible
- connect deployment-level actions to their resulting operation history

### Minimum UI requirements

- operation timeline or table showing:
  - operation type
  - operation state
  - start time
  - state entered time
  - linked app instance
  - error detail when present
- operation history accessible from deployment details

### UX guidance

- present this as lifecycle history rather than raw job records
- reuse existing job visualization patterns where possible, but keep MEC terminology visible for enabled users

### Acceptance criteria

- enabled users can see whether instantiate, terminate, or operate completed or failed
- failures expose useful human-readable error context

## Workstream 5: MEPM Administration

### Objective

Provide an operational UI for managing MEPM resources already implemented server-side.

### Requirements

- expose MEPM resource list and detail views
- allow users to inspect endpoint, host linkage, status, capabilities, and resources
- expose existing operational actions:
  - check health
  - query capabilities
  - query resources

### Minimum UI requirements

- MEPM list page or MEC administration section
- MEPM detail view with:
  - name
  - endpoint
  - status
  - associated MEC host or NuvlaEdge
  - last check
  - capabilities summary
  - resources summary
- action buttons with visible success/failure feedback

### UX guidance

- this may live under a dedicated MEC admin area or under an existing advanced admin surface
- it must remain hidden behind the ETSI MEC feature flag

### Acceptance criteria

- enabled users can inspect and refresh MEPM state from the UI
- placement-related troubleshooting becomes possible without direct API calls

## Workstream 6: MEC Subscriptions and Notifications

### Objective

Expose the notification subscription model needed for demos, integrations, and validation.

### Requirements

- allow creation, listing, and deletion of MEC subscriptions
- support both subscription types already present server-side
- make filter setup understandable

### Minimum UI requirements

- subscription list view
- create-subscription form with:
  - subscription type
  - callback URI
  - optional app instance filter
  - optional operation occurrence filter
- delete action with confirmation

### UX guidance

- this can be positioned as an advanced feature inside a MEC admin area
- it does not need to be the first UI increment, but it should be part of the planned MEC UI scope

### Acceptance criteria

- enabled users can manage subscriptions without raw API usage
- disabled users do not see subscription UI

## Recommended Delivery Phases

## Phase 1: Hidden MEC Foundation

Deliver:

- ETSI MEC feature flag
- MEC subtype support in the Apps UI
- MEC-safe rendering paths so `application_mec` does not break existing views

Success condition:

- the codebase can safely contain MEC UI without affecting users who do not enable the flag

## Phase 2: Operator-Useful MEC Surface

Deliver:

- MEC-aware deployment details
- lifecycle actions and status
- lifecycle operation history

Success condition:

- an enabled user can create, inspect, and operate a MEC app instance from the UI

## Phase 3: MEC Operations and Admin

Deliver:

- MEPM administration UI
- subscription management UI

Success condition:

- an enabled operator can manage and troubleshoot the MEC environment from the UI

## Information Architecture Recommendation

The preferred structure is:

- keep MEC applications inside `Apps`
- keep MEC app instance visibility inside `Deployments`
- add a dedicated MEC admin surface only for capabilities that do not fit naturally into existing pages, such as:
  - MEPM management
  - subscriptions

This avoids splitting the product into a generic Nuvla area and a separate MEC mini-product.

## Success Metrics

The MEC UI increment is successful if:

- non-MEC users report no meaningful increase in UI complexity
- enabled users can complete core MEC workflows without using the API browser
- demo and validation flows can be executed from the UI with reduced manual API steps
- support and troubleshooting effort for MEPM selection and lifecycle state visibility decreases

## Risks

### Risk 1: MEC leaks into the default UI

If gating is inconsistent, users without MEC enabled may see partial terminology or broken entry points.

Mitigation:

- use one shared feature flag everywhere
- make flag checks part of the acceptance criteria for every MEC UI story

### Risk 2: Generic and MEC lifecycle semantics drift apart

If the UI mixes generic deployment semantics with MEC semantics carelessly, users may misunderstand action outcomes.

Mitigation:

- show MEC state explicitly for MEC-backed deployments
- label lifecycle operations in MEC terms when the MEC feature is active

### Risk 3: Too much MEC detail appears too early

If the first UI iteration exposes every standards concept at once, even enabled users may find the experience noisy.

Mitigation:

- phase the rollout
- prioritize app subtype support, deployment visibility, and MEPM diagnostics first
- keep subscriptions and deeper admin flows in later phases

## Open Product Decisions

- whether MEPM management should appear as a separate sidebar page or as an advanced subsection under an existing area
- whether the MEC application editor should default to structured forms, raw AppD editing, or a hybrid model
- whether operation history should be a dedicated tab or embedded inside the existing jobs view
- whether subscription management belongs in the first operator release or a later admin-focused increment

## Final Recommendation

The recommended product path is:

1. gate all ETSI MEC UI behind one feature flag
2. make MEC a first-class subtype inside Apps
3. make MEC lifecycle state visible inside Deployments
4. add MEPM and subscription administration as advanced enabled-only surfaces

This gives Nuvla a usable ETSI MEC UI without disturbing the default product experience for existing users.
