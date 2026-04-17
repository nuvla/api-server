# Nuvla Validation Strategy for Standards Compliance

## Purpose

This note provides a rough validation strategy for the developments related to standards compliance of the Nuvla edge orchestration solution. The goal is to validate that the implemented enhancements behave correctly, are interoperable, and provide credible alignment with the targeted standards compliance expectations.

## Validation Objective

The validation activity should demonstrate that:

- Nuvla behaves as a recognizable ETSI MEC Orchestrator for the targeted scope.
- The implemented ETSI MEC-aligned interfaces and workflows behave correctly for the validated scope.
- The selected compliance features are interoperable with the targeted standards-aligned control surfaces and workflows.
- Compliance is established through the targeted ETSI MEC specifications, which in turn support alignment with relevant 3GPP edge-management expectations.

## Scope of Validation

The validation should focus on the standards-related developments considered most relevant and feasible in the compliance study:

- `ETSI GS MEC 003`: orchestrator role and responsibilities.
- `ETSI GS MEC 010-2`: application lifecycle management APIs, objects, and state transitions.
- `ETSI GS MEC 011`: application, service, and topology-related information models where adopted.
- `ETSI GS MEC 037`: application package format, descriptors, onboarding, and versioning.
- Practical alignment with relevant 3GPP edge-management expectations as a consequence of ETSI MEC compliance.

## Validation Principles

- Validate against the selected standards requirements, not only against internal behavior.
- Prefer validation setups that are simple, reproducible, and traceable.
- Combine positive, negative, and interoperability scenarios.
- Keep traceability between requirement, implemented feature, test scenario, and evidence.

## Main Validation Areas

### 1. Interface Conformance

Validate that the exposed interfaces follow the expected standards-aligned structure and semantics for the targeted subset of the ETSI MEC API surface.

- Check the targeted `Mm1` operations for onboarding, instantiate, terminate, query, and related lifecycle control.
- Check the targeted `Mm3` interactions for host/resource preparation and lifecycle execution.
- Verify request/response structures, identifiers, state models, and error handling against the selected ETSI MEC profiles.
- Verify that unsupported operations are rejected cleanly and explicitly.

### 2. Application Package Onboarding

Validate onboarding of standards-aligned application packages.

- Verify acceptance of valid MEC application packages and descriptors.
- Verify package metadata, descriptor parsing, versioning, and traceability.
- Verify rejection behavior for invalid descriptors, missing metadata, or failed integrity-related checks where such checks are implemented.

### 3. Lifecycle Management

Validate the core MEC 010-2 lifecycle behavior.

- Instantiate an application from a valid onboarded package.
- Query lifecycle operation status and application instance state.
- Terminate an application and verify final state cleanup.
- Validate idempotency and error behavior for invalid transitions.
- Verify that lifecycle states exposed externally are consistent with Nuvla’s internal deployment behavior.

### 4. Host Targeting and Resource Checks

Validate the currently supported host-targeting and resource-checking behavior for the selected scope.

- Verify explicit host targeting through selected fleet definitions or equivalent targeting inputs.
- Verify filtering or targeting behavior based on the criteria currently supported in the implementation.
- Verify basic compatibility and resource checks for the selected targets, such as architecture, CPU, RAM, and disk where applicable.
- Verify behavior when the selected targets do not satisfy the declared requirements.
- Verify that target selection and requirement-check outcomes are observable in logs, events, or API responses where available.

### 5. Security Checks

Validate the relevant security-related controls in the onboarding and instantiation pipeline for the selected scope.

- Verify authentication, authorization, and access-control behavior for standards-facing APIs.

### 6. Monitoring, Events, and Operational Evidence

Validate that the orchestration workflows are observable and auditable.

- Verify lifecycle events and operation outcomes.
- Verify logs and monitoring data for onboarding, placement, instantiate, and terminate actions.
- Verify that failures expose useful and standards-aligned error information.
- Where feasible, verify alarm/performance outputs needed to support the ETSI MEC compliance evidence.

## Validation Methods

The validation should combine several methods:

- API-level conformance checks against the selected ETSI MEC endpoints and payload semantics for the targeted subset of interfaces.
- Functional checks for onboarding, deployment, and termination workflows where feasible.
- Negative tests for invalid input, missing resources, failed implemented security checks, and unsupported operations.
- Regression tests to confirm that existing Nuvla deployment capabilities are not broken by the standards-compliance layer.
- Interoperability-oriented validation through standards-aligned interface and workflow checks.
- Traceability checks linking each targeted requirement to the corresponding implementation element and supporting validation evidence.

## Proposed Validation Scenarios

The minimum validation campaign could include the following scenarios:

1. Onboard a valid MEC package and verify successful registration and metadata storage.
2. Attempt onboarding of an invalid or unsigned package and verify proper rejection.
3. Instantiate an application through the standards-aligned northbound flow and verify successful deployment on the selected edge host.
4. Query application and operation status during and after deployment.
5. Terminate the application and verify state cleanup and final reporting.
6. Submit supported host-targeting or filtering inputs and verify the resulting target selection and requirement-check behavior.
7. Verify error responses for unsupported or malformed requests.
8. Use targeted integration tests and representative deployment conditions to increase confidence in robustness, where feasible.

## Evidence to Collect

For each scenario, the following evidence should be collected:

- Tested requirement or targeted standard feature.
- Test setup and preconditions.
- API requests and responses.
- Operation status and exposed state transitions.
- Logs, events, and monitoring outputs.
- Deployment outcome on target edge resources.
- Screenshots or traces from the validation runs when useful.
- Pass/fail result and open issues.

## Acceptance Criteria

The developments can be considered successfully validated when:

- The targeted subset of `Mm1` and `Mm3` functions operates correctly for the selected scope.
- The main MEC 010-2 lifecycle workflows complete successfully and expose coherent states and errors.
- MEC package onboarding works for valid inputs and fails predictably for invalid ones.
- The supported target-selection and requirement-checking behaviors operate correctly for the selected scenarios.
- Security checks are enforced where implemented.
- No major regression is introduced in Nuvla’s existing orchestration behavior.
- The collected evidence supports the claim of practical ETSI MEC compliance for the selected scope, with any related 3GPP alignment deriving from that compliance path.

## Deliverables of the Validation Activity

The validation activity should produce lightweight but clear outputs:

- A requirement-to-test mapping table.
- A short execution report summarizing tested scenarios and outcomes.
- A list of open gaps, limitations, and deferred items.
- Selected evidence from validation runs and API traces.

