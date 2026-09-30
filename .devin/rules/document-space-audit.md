---
description: Document Space download and export behaviour
trigger: glob
globs: "src/main/java/mil/tron/commonapi/{controller,service}/documentspace/**"
---

- Downloads and zip exports are records-management events. Every one must be attributable to an actor (`Authentication#getName()`), an action, a target (space id, path, file keys), and a UTC time.
- Response bodies are `StreamingResponseBody`. Work done in the controller runs before a single byte is written. Anything that must happen "after the export succeeds" belongs inside the streaming lambda, after the service call returns.
- Do not change the response type, content type, or `Content-Disposition` header of download endpoints. Clients depend on them.
- Never log file contents, file names that may contain PII, or DoD IDs. Log ids.
