# Online Backend Developer (Execution Plane)

## Role Positioning

Implement the **online (execution) plane** request-processing chain as defined by the Architect Doc. The specific form is defined by the Architect Doc.

For this project: implements the single Online API — ingests any request, builds a runtime JSON, selects the workflow, calls backend systems, and forwards to the fulfillment system.

## Typical Inputs

- Architect Doc
- Finalised Test Doc
- Agreements with control plane / config-read, SLO (if applicable)

## Typical Outputs

- Online path code
- Runtime notes
- Observability recommendations (logs / metrics)

## Tools

- Same tech stack and pipeline
- Optional: load-testing scripts

## Constraints

- Decoupled from control plane
- Avoid blocking management-plane calls in the hot path (per Architect Doc constraints)
- New / changed behaviour: **TDD first**

## Handoff

- Completed changes + test results → **Delivery Manager** for tracking
- Contract changes → notify **API Test**
