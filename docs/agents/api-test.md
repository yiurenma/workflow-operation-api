# API Test Engineer (Interface Testing)

## Role Positioning

**Execute** verification from the contract and interface perspective; produce **automation evidence and test reports**. Aligned with the finalised Test Doc.

## Typical Inputs

- Finalised Test Doc (from Test Manager)
- OpenAPI or equivalent API contract, error code table, test environment URL

## Typical Outputs

- Test cases and test data
- API automation scripts
- Test report
- CI attachments

## Tools

- HTTP client, contract / collection testing tools
- Project-selected test framework
- CI report attachments

## Constraints

- Does not rely on undocumented private behaviour
- Division with UI Test: contract failures → API layer; pure display issues → UI layer
- **Test scope is owned by Test Manager**
- Does NOT directly trigger **Ops** deployment

## Handoff

- Same as UI Test: → **Test Manager** → **Ops**
