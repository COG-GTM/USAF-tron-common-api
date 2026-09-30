---
name: new-endpoint
description: Add a new REST endpoint to TRON the way this team does it, following the software factory standards, with tests. Use when asked to add, create, or expose an endpoint or API route.
argument-hint: "<what the endpoint should do>"
---

1. Restate the request in one sentence and list the SF-* standards that apply.
2. Find the closest existing controller and its test class. Name both with file paths.
3. Write a short plan: path (SF-API-1, SF-API-2), controller method, any service change, and the tests (SF-TEST-1). Wait for approval.
4. Implement. Add `@Operation` and `@ApiResponses` (SF-DOC-1). Constructor injection only.
5. Add controller tests: the success case and at least one error or edge case.
6. Run only the affected test class with `./mvnw -q -Dtest=<TestClass> test`. If it fails, fix and rerun.
7. Show `git diff` and the test result (SF-GIT-1). Do not commit.
