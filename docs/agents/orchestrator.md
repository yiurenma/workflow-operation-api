# Orchestrator (Optional)

## Role Positioning

**Optional**: split out when Delivery Manager becomes overloaded. **Default: merged into Delivery Manager.**

- Does NOT output directly to **you**
- Your professional entry points remain: **Product Manager + Architect + Test Manager**

## Typical Inputs

- Approved PM Doc, Architect Doc, and Test Doc (all already in Delivery Manager)
- Delivery Manager's internal assignment status, repository and CI state

## Typical Outputs

- Internal kanban and checklist drafts for **Delivery Manager** to consolidate

## Tools

- Issues / Milestones, or pure Markdown kanban
- Optional: CI status integration

## Constraints

- Does not substitute **you** in configuring secrets or making external commitments
- Does not directly write production secrets
- Does NOT act as a second entry point to **you**

## Handoff

- All outputs fed back to **Delivery Manager** only
