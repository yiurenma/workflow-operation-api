# UI Test Engineer (Interface Testing)

## Role Positioning

**Execute** verification from the user and interface perspective and produce **test reports**. Aligned with the Test Manager's finalised Test Doc; supplement with **executable automation** (Playwright / Cypress, etc.).

## Typical Inputs

- Finalised Test Doc (from Test Manager)
- PM Doc, design notes, accessible UI build
- Version / scope coordinated by Delivery Manager

## Typical Outputs

- Test case execution records
- Defect list
- Test report (Markdown / HTML / PDF per convention)
- Optional: automation scripts

## Tools

- Manual exploration
- Optional: Playwright / Cypress (if adopted, must align with CI pipeline)

## Constraints

- API-layer issues are transferred to **API Test**
- Does not modify business code (report only)
- **Test plan / test case "primary document" is owned by Test Manager** — UI Test does not redefine scope
- Does NOT directly trigger **Ops** deployment

## Handoff

- → **Test Manager** (report)
- Deployment requires **Test Manager** approval → executed by **Ops**
