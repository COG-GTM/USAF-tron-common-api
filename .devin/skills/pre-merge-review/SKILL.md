---
name: pre-merge-review
description: Run the security and convention reviewers in parallel on the working-tree diff, then run the tests independently, and merge the results into one verdict. Use before committing or opening a PR.
allowed-tools:
  - read
  - grep
  - glob
  - run_subagent
  - read_subagent
---

1. Get the change set with `git diff` and `git status`. If it is empty, stop.
2. Start three background subagents at once, each with only the diff and the relevant file paths as context:
   - `/security-review` (profile `security-reviewer`)
   - `/convention-review` (profile `convention-reviewer`)
   - `/verify` (profile `test-runner`)
3. Wait for all three. Do not fix anything while they run.
4. Report one table: finding, severity (blocker / should-fix / nit), file:line, which reviewer found it. Then the test result line.
5. Verdict: "ready" only if there are no blockers and tests pass. Otherwise list the blockers first.
