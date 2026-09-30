---
trigger: always_on
---

# Software factory standards (demo)

These are the team's standards for this repository. Follow them in every plan, change, and review, and name the standard (for example SF-API-1) when it shapes a decision.

- **SF-API-1** New endpoints are v2 only. Never add a new path under v1; v1 is frozen.
- **SF-API-2** Path segments are lowercase, plural nouns, kebab-case (`/rank/branches`, not `/rank/getBranches`).
- **SF-DOC-1** Every endpoint has `@Operation` with a summary and a description, and `@ApiResponses` listing every status it can return.
- **SF-TEST-1** Every new endpoint ships with a controller test for the success case and at least one error or edge case.
- **SF-LOG-1** Never log DoD IDs, names, emails, or other PII.
- **SF-GIT-1** Show the diff and the test result before any commit. Never push.
