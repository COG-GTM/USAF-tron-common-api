---
name: standup
description: Write my standup update for the current repository (done, next, blockers) from recent commits and uncommitted changes. Read-only.
permissions:
  allow:
    - Exec(git log)
    - Exec(git status)
    - Exec(git diff)
  deny:
    - edit
---

Write a standup update for the person asking, from what Git shows in this repository.

1. Run `git log --since="1 day ago" --oneline`. If it prints nothing, run `git log -5 --oneline` instead.
2. Run `git status --short` and `git diff --stat` to see work that is not committed yet.
3. Reply with three short bullets, in plain language a program manager can read:
   - **Done:** what was committed.
   - **In progress:** what is changed but not committed, and whether its tests were run in this session.
   - **Blockers:** anything unfinished or failing, or "none".
4. Do not edit, commit or push anything.
