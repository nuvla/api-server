# Nuvla MEC UI Execution Plan

## Purpose

This document translates `Nuvla_MEC_UI_PRD.md` into an execution-oriented frontend backlog. It is intended to support milestone planning, sprint slicing, engineering assignment, and implementation hand-off for the ETSI MEC UI work.

The execution strategy follows one product rule:

- all ETSI MEC UI remains hidden behind a single feature flag

The execution strategy also follows one delivery rule:

- land the visibility boundary first, then make MEC safe in existing Apps and Deployments flows, then add richer operational surfaces

## Recommended Hand-Off Mode

For execution, this document should be read in milestone-first order, with UI workstreams aligned to the same sequence.

Reason:

- milestones define the delivery and rollout boundary
- aligned workstreams keep engineering tasks consistent with product sequencing
- the feature-flag-first approach protects the existing Nuvla UX while MEC work is still incomplete

Practical rule:

- use `milestones` as the primary delivery sequence
- use `UIWS` tags as the matching execution-stream labels for the same ordered steps

## UI Workstream Tags Used in Hand-Off

When tasks are handed off, use the following labels:

- `UIWS1` Feature flag foundation
- `UIWS2` MEC application subtype in Apps
- `UIWS3` MEC-aware deployment details and actions
- `UIWS4` MEC lifecycle operation visibility
- `UIWS5` MEC administration: MEPM and subscriptions

## Planning Assumptions

- the ETSI MEC UI is optional and must not increase complexity for non-MEC users
- a UI feature flag is sufficient for the current rollout boundary
- Apps and Deployments remain the primary user surfaces for MEC
- dedicated MEC admin UI is added only where generic Nuvla views are insufficient
- the first implementation increments should favor safety and visibility over full standards coverage
- later UI milestones may depend on stable backend payloads and endpoint behavior already being available server-side

## Backlog by Milestone

## Milestone 1: Feature Flag Boundary Established

### Goal

Create the visibility boundary that keeps all ETSI MEC UI hidden unless explicitly enabled.

### Implementation note

The existing UI already has a feature-flag mechanism and sidebar gating pattern. This milestone should reuse that mechanism rather than introduce a second flag system.

### Primary code touchpoints

- `ui/code/src/cljs/sixsq/nuvla/ui/pages/about/utils.cljs`
- `ui/code/src/cljs/sixsq/nuvla/ui/pages/about/events.cljs`
- `ui/code/src/cljs/sixsq/nuvla/ui/pages/about/subs.cljs`
- `ui/code/src/cljs/sixsq/nuvla/ui/main/views_sidebar.cljs`
- any new or existing page registration that needs `feature-flag-kw`

### Key tasks

- `UIWS1` Add a new feature flag key for ETSI MEC, for example `etsi-mec`.
- `UIWS1` Add a human-readable description to the experimental feature list.
- `UIWS1` Reuse the current persisted feature-flag model so the flag behaves like existing optional UI capabilities.
- `UIWS1` Define the visibility rule for MEC UI surfaces:
  - sidebar items
  - app subtype options
  - app detail panes
  - deployment detail sections
  - MEC admin pages
  - action buttons
- `UIWS1` Ensure flag-disabled users cannot reach incomplete MEC UI through standard creation flows or normal navigation.

### Dependencies

- none; this milestone is the required execution entry point

### Exit criteria

- one ETSI MEC feature flag exists in the UI feature-flag system
- all planned MEC UI surfaces can be gated by that single flag
- disabled users do not see MEC-specific navigation or subtype entry points

## Milestone 2: MEC Application Subtype Safe and Visible in Apps

### Goal

Make `application_mec` a first-class app subtype in the existing Apps UI.

### Implementation note

This milestone should first eliminate incorrect fallback behavior. Today, unsupported subtypes risk falling through generic rendering paths instead of being handled explicitly.

### Primary code touchpoints

- `ui/code/src/cljs/sixsq/nuvla/ui/pages/apps/utils.cljs`
- `ui/code/src/cljs/sixsq/nuvla/ui/pages/apps/views.cljs`
- `ui/code/src/cljs/sixsq/nuvla/ui/pages/apps/views_detail.cljs`
- `ui/code/src/cljs/sixsq/nuvla/ui/pages/apps/apps_application/views.cljs`
- app subtype-specific events/subs/spec files in `ui/code/src/cljs/sixsq/nuvla/ui/pages/apps/`

### Key tasks

- `UIWS2` Add an MEC subtype constant and integrate it into subtype utility helpers.
- `UIWS2` Update add-application flows so the MEC subtype option appears only when the ETSI MEC flag is enabled.
- `UIWS2` Extend module-detail branching so `application_mec` does not fall back to project behavior.
- `UIWS2` Extend subtype rendering so existing subtype labels do not fail or omit MEC.
- `UIWS2` Define the first MEC-specific app detail structure:
  - overview
  - details
  - configuration sections relevant to AppD-backed applications
- `UIWS2` Decide the minimal editing model for the first UI increment:
  - structured summary first
  - raw descriptor inspection later if needed
- `UIWS2` Preserve existing Docker, Kubernetes, Helm, and bouquet behavior without regressions.

### Dependencies

- Milestone 1 feature flag boundary
- stable understanding of the `application_mec` data shape used by the backend

### Exit criteria

- enabled users can create or open an `application_mec` app without UI breakage
- subtype labels and detail rendering explicitly recognize MEC
- disabled users do not see the MEC subtype option

## Milestone 3: MEC-Aware Deployment Details

### Goal

Expose MEC lifecycle semantics inside the existing deployment detail experience.

### Implementation note

This milestone should extend Deployments rather than create a separate app-instance screen first. The objective is to make a deployment clearly readable as a MEC-backed app instance when applicable.

### Primary code touchpoints

- `ui/code/src/cljs/sixsq/nuvla/ui/pages/deployments_detail/subs.cljs`
- `ui/code/src/cljs/sixsq/nuvla/ui/pages/deployments_detail/views.cljs`
- supporting events/spec files under `ui/code/src/cljs/sixsq/nuvla/ui/pages/deployments_detail/`
- any shared utilities used for operation/action gating

### Key tasks

- `UIWS3` Define the UI detection rule for a MEC-backed deployment:
  - module subtype
  - MEC-specific parameters
  - MEC-specific job metadata
- `UIWS3` Add MEC-only overview fields when the deployment is MEC-backed:
  - app instance id
  - instantiation state
  - operational state
  - target MEC host
  - selected MEPM
- `UIWS3` Add MEC lifecycle actions when state transitions are valid:
  - instantiate
  - terminate
  - operate start
  - operate stop
- `UIWS3` Keep existing generic deployment actions unchanged for non-MEC deployments.
- `UIWS3` Ensure the MEC state is visible before users need to inspect lower-level job history.

### Dependencies

- Milestone 1 flag boundary
- Milestone 2 subtype-safe MEC app handling
- stable availability of MEC fields through deployment/job payloads or frontend fetches

### Exit criteria

- enabled users can distinguish a MEC-backed deployment from a generic deployment
- enabled users can see MEC lifecycle state in deployment detail
- MEC actions appear only when both the feature flag and deployment conditions match

## Milestone 4: Lifecycle Operation Visibility

### Goal

Make asynchronous MEC operations visible, traceable, and understandable in the UI.

### Implementation note

This milestone should reuse existing job-oriented visual patterns where useful, but must present the behavior in MEC terms for enabled users.

### Primary code touchpoints

- `ui/code/src/cljs/sixsq/nuvla/ui/common-components/job/`
- `ui/code/src/cljs/sixsq/nuvla/ui/pages/deployments_detail/views.cljs`
- deployment detail events/subs that already fetch jobs or related state
- any new MEC-specific operation view components

### Key tasks

- `UIWS4` Define the UI model for operation occurrences backed by MEC lifecycle operations.
- `UIWS4` Add a lifecycle history panel, tab, or timeline linked from deployment details.
- `UIWS4` Show at least:
  - operation type
  - operation state
  - start time
  - state-entered time
  - linked app instance
  - readable error detail if present
- `UIWS4` Ensure users can correlate a lifecycle action with the resulting operation occurrence.
- `UIWS4` Decide whether this surface is:
  - a dedicated MEC tab
  - an MEC section inside jobs
  - a deployment-level timeline view

### Dependencies

- Milestone 3 MEC deployment context
- stable backend representation of operation occurrences or job mappings

### Exit criteria

- enabled users can see whether instantiate, terminate, or operate is pending, completed, or failed
- failures are readable without raw API inspection

## Milestone 5: MEC Administration Surface

### Goal

Add the advanced MEC administration UI that does not fit naturally into Apps or Deployments.

### Implementation note

This milestone should stay behind the same feature flag and should not ship until the embedded MEC UX in Apps and Deployments is already coherent.

### Primary code touchpoints

- routing and page registration for any new MEC admin route
- `ui/code/src/cljs/sixsq/nuvla/ui/main/views_sidebar.cljs`
- new MEC admin page components under `ui/code/src/cljs/sixsq/nuvla/ui/pages/`
- any shared table/form/action components reused from other admin views

### Key tasks

- `UIWS5` Define the placement of MEC administration in the UI:
  - dedicated sidebar page
  - advanced subsection under an existing page
- `UIWS5` Add MEPM inspection and action support:
  - list
  - detail
  - check health
  - query capabilities
  - query resources
- `UIWS5` Add MEC subscription management:
  - list
  - create
  - delete
  - optional filtering controls
- `UIWS5` Keep admin surfaces hidden for users without the flag.
- `UIWS5` Ensure admin pages feel consistent with existing Nuvla list/detail/action patterns.

### Dependencies

- Milestone 1 flag boundary
- stable backend behavior for MEPM and subscription resources
- Milestones 2 to 4 complete enough that MEC terminology and navigation are already coherent

### Exit criteria

- enabled operators can inspect MEPM readiness from the UI
- enabled operators can manage MEC subscriptions from the UI
- non-MEC users are unaffected by the new admin surface

## Cross-Cutting Backlog Items

## A. Visibility and Gating Consistency

- every MEC UI surface must use the same feature flag
- do not hide only the sidebar while leaving deep links or creation paths visible
- treat gating checks as part of the acceptance criteria for every milestone

## B. Terminology Consistency

- use MEC-native terms where the feature is explicitly MEC-facing
- keep generic Nuvla terms for non-MEC workflows
- ensure the same deployment does not present conflicting lifecycle language in the same screen

## C. Safe Incremental Rendering

- prefer adding guarded sections inside existing pages before introducing fully separate pages
- prevent unsupported subtype fallback behavior early
- make partially implemented MEC screens impossible to reach when the flag is disabled

## D. Verification

- verify both flag-disabled and flag-enabled behavior for every milestone
- test both MEC-backed and non-MEC resources in shared pages
- avoid introducing regressions in Docker, Kubernetes, Helm, and bouquet flows

## Recommended Sprint Slicing

### Sprint slice 1

- Milestone 1
- Milestone 2 subtype recognition and safe rendering foundation

### Sprint slice 2

- Milestone 2 visible MEC app subtype entry points
- Milestone 3 MEC deployment overview fields

### Sprint slice 3

- Milestone 3 MEC lifecycle actions
- Milestone 4 lifecycle operation visibility

### Sprint slice 4

- Milestone 5 MEPM administration
- Milestone 5 subscription management

## Recommended Agent Execution Sequence

Use this section when handing implementation to the agent. The sequence is intentionally milestone-first, and each step aligns with the matching UI workstream.

### Sequence 1

- complete `Milestone 1 / UIWS1`
- then start the `Milestone 2 / UIWS2` subtype-safety tasks

### Sequence 2

- finish `Milestone 2 / UIWS2` subtype support and guarded entry points
- only then start `Milestone 3 / UIWS3`

### Sequence 3

- once `Milestone 3 / UIWS3` exposes MEC deployment state, start `Milestone 4 / UIWS4`

### Sequence 4

- once Apps and Deployments MEC surfaces are coherent, complete `Milestone 5 / UIWS5`

## Definition of Done for MEC UI Features

A MEC UI feature should only be considered done when all of the following are true:

- it is fully hidden when the ETSI MEC feature flag is disabled
- it renders correctly when the ETSI MEC feature flag is enabled
- it does not regress non-MEC Apps or Deployments flows
- it uses consistent MEC terminology and state presentation
- it is reachable through a coherent user flow rather than only through direct URL entry

## Immediate Next Tasks

The most valuable near-term tasks, in recommended implementation order, are:

1. `Milestone 1 / UIWS1`: add the `etsi-mec` feature flag
2. `Milestone 1 / UIWS1`: gate all planned MEC entry points behind that flag
3. `Milestone 2 / UIWS2`: add explicit `application_mec` subtype handling in the Apps UI
4. `Milestone 2 / UIWS2`: add MEC-safe subtype labeling and detail rendering
5. `Milestone 3 / UIWS3`: expose MEC state in deployment detail for MEC-backed deployments
6. `Milestone 4 / UIWS4`: add lifecycle operation visibility
7. `Milestone 5 / UIWS5`: add MEPM and subscription administration surfaces

## Final Recommendation

The implementation should start with the feature-flag boundary, then make MEC safe and explicit inside Apps and Deployments, and only afterward add dedicated MEC administration surfaces.

This keeps the default Nuvla UX stable while still creating a credible ETSI MEC UI path for enabled users.
