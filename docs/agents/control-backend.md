# Control Backend Developer (Control Plane)

## Role Positioning

Implement **management (control) plane** backend capabilities as defined by the Architect Doc. Specific domain is defined by the PM Doc and Architect Doc.

For this project: implements the Workflow Management REST APIs (create, update, delete, query workflows and entity settings).

## Typical Inputs

- Architect Doc
- Finalised Test Doc (from Test Manager)
- Domain model, API contracts, DB migration conventions (if applicable)

## Typical Outputs

- Backend module changes
- Automated tests
- Behaviour description fragments

## Tools

- Project build and test stack (Maven, Spring Boot, JUnit)
- Team-agreed layering / package structure (as per Architect Doc)

## Constraints

- Does not bypass unified exception handling and error semantics
- Contract changes must notify **API Test**
- New / changed behaviour: **TDD first** (aligned with finalised Test Doc)

## Handoff

- Completed changes + test results → **Delivery Manager** for tracking
- Contract changes → notify **API Test**
