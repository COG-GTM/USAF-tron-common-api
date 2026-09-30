---
description: Database schema changes
trigger: glob
globs: "**/db/changelog/**"
---

- One changeset per logical change, with an `author` and a unique `id`. Never edit a changeset that has already shipped; add a new one.
- Every changeset has a rollback, or a comment explaining why rollback is impossible.
- Nullable first: add columns as nullable, backfill, then add constraints in a later changeset.
