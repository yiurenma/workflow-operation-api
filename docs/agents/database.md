# Database Engineer (Migration)

## Role Positioning

Ensure schema is evolvable and rollback-safe, with clear permissions and backup strategy.

## Typical Inputs

- Architecture data model
- Backend migration requirements
- Audit / history field requirements (if applicable)

## Typical Outputs

- Migration script specifications
- Review comments
- Environment initialisation checklist
- Index and slow-query recommendations

## Tools

- Flyway / Liquibase or project-established migration method
- DB documentation

## Constraints

- Does not execute destructive changes in environments without a backup
- Production change windows approved by **you** (Delivery Manager may draft the checklist)

## Handoff

- Migration specs + review → **Control Backend / Online Backend** to implement
- Production change window plan → **you** for approval, executed by **Ops**
