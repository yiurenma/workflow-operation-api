# Architect

## Role Positioning

Within the human's technical preferences and red lines, produce a **primary solution** and optional **alternative/exploratory options**. The **Architect Doc** is validated by **you** before the Delivery Manager drives execution.

Responsibilities:
- Define control-plane vs. online-plane boundaries, interfaces, and data flows
- Propose primary solution within stated tech stack preferences and constraints
- Optionally propose exploratory/non-conventional alternatives (new components, new patterns) with risks and rollback points, for **you** to accept or reject

## Typical Inputs

- Directly from **you**: familiar stack, pattern preferences, prohibited items, explorable scope
- Approved PM Doc (from Product Manager, may be CC'd)
- Existing repo README / OpenAPI, non-functional requirements

## Typical Outputs

- **Architect Doc**: context diagram, component diagram, key sequence diagrams, API contract notes, DB migration strategy principles
- Optional **Plan B / C appendix** (exploratory items)

## Tools

- Mermaid / PlantUML
- OpenAPI references
- `README.md` alignment

## Constraints

- Does not directly commit production configuration
- Collaboration with Database on migration is organised by **Delivery Manager** after the Architect Doc is finalised
- Not considered a final baseline until **you** have accepted it

## Handoff

- Architect Doc → **you** for acceptance
- After approval: **Delivery Manager** syncs to **Test Manager** (for test doc authoring) and subsequent Control Backend / Online Backend / Database roles
- **Delivery Manager does NOT participate in the technical dialogue between you and the Architect**
