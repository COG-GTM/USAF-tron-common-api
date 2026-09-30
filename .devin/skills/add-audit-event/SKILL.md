---
name: add-audit-event
description: Make a TRON endpoint record who, what, and when for every call, the way this codebase does it, without changing the response shape. Use for audit, export, download, or records-management tickets.
argument-hint: "<endpoint or plan file>"
permissions:
  allow:
    - Write(src/main/java/**)
    - Write(src/test/java/**)
    - Write(docs/agent-plans/**)
  ask:
    - Write(src/main/resources/db/**)
---

Read the plan in `docs/agent-plans/` first if one exists, and tick its `## Status` items as you go.

1. Locate the controller method and the service method it calls. Note whether the response is a `StreamingResponseBody`. If it is, the audit write must happen inside the streaming lambda after the service call returns; code in the controller body runs before anything is sent.
2. Find the actor. Take it from the `Authentication` parameter (`authentication.getName()`). If the endpoint has no `Authentication` parameter, add one; that does not change the response shape.
3. Reuse an existing mechanism before creating one. Check, in order: `DocumentSpaceMetadataService` (per-file `lastDownloaded`), `EventManagerService.recordEventAndPublish`, and `HttpTraceService`. Say which you chose and why in one sentence.
4. If, and only if, a new table or column is required, add a Liquibase changeset under `src/main/resources/db/changelog/` and include it from `db.changelog-master.xml`. That path prompts for approval; explain the schema change before writing it.
5. Tests:
   - Controller test (`@WebMvcTest` style, `@MockBean` services): assert the audit call happens exactly once per request with the right actor, action, and target, using `ArgumentCaptor`. Use `asyncDispatch` for streaming responses.
   - Assert the existing status, content type, and `Content-Disposition` are unchanged.
6. Run `./mvnw -q test -Dtest='DocumentSpaceControllerTest'` (or the class you touched). Stop and report if anything fails. Do not widen the test run until this passes.
7. Show the diff. Do not commit.
