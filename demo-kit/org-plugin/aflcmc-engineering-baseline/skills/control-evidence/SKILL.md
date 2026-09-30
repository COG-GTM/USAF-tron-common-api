---
name: control-evidence
description: Produce an assessor-ready evidence note that maps the current diff to the NIST SP 800-53 controls a ticket cites. Use after implementation and before a PR, or when asked for control, ATO, or audit evidence.
argument-hint: "<TICKET-ID>"
allowed-tools:
  - read
  - grep
  - glob
---

1. Read `docs/agent-plans/$ARGUMENTS.md` for the ticket's controls. If there is no plan, ask for the control ids.
2. Read the diff (`git diff` against the merge base) and the tests it adds.
3. Write `docs/agent-plans/$ARGUMENTS-evidence.md`:
   - One row per control: control id, what the change does for it, file:line of the implementation, file:line of the test that proves it.
   - A "Not addressed" list for anything the control needs that the diff does not do (for example AU-9 protection of the stored records).
4. Do not claim a control is satisfied; say what evidence exists. An assessor decides.
