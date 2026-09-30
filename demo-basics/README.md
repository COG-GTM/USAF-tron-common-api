# AFLCMC 201: agents from first principles

A 37-minute live demo for developers who have not used an AI agent before. It uses one small, real endpoint in this repository and adds one idea at a time. Each layer is a file you can open, so nothing is hidden.

| # | Layer | The one idea | Minutes |
|---|-------|--------------|---------|
| 1 | What an agent is | A model that can use tools in a loop, with you approving | 3 |
| 2 | Reading code | It reads the real files, and you ask where each answer came from | 4 |
| 3 | Standards | Write the standard once in a file; every session loads it | 5 |
| 4 | Plan, then build | Plan first, approve each edit | 6 |
| 5 | Checking its work | Read the diff, run the test, and have a second agent with fresh eyes review it | 5 |
| 6 | Guardrails | Some things it is never allowed to do, whatever it is told | 3 |
| 7 | Skills | Save a good procedure once; anyone reruns it with one command. Team skills live in the repo, personal ones in your home folder | 5 |
| 8 | Sharing | Repo, user, org: the same files at three levels, packaged as a plugin | 4 |

Everything is synthetic and offline. The only secret in the demo is a fake `.env` file that says it is fake.

The deeper demo (MCP, hooks, parallel subagents, prompt injection, enterprise plugin lockdown) is on the `demo/aflcmc-201` branch. Use it for follow-up questions or a 301.

## Before the room fills (10 minutes)

```bash
git clone https://github.com/COG-GTM/USAF-tron-common-api.git
cd USAF-tron-common-api
git checkout demo/aflcmc-201-basics
export JAVA_HOME=$(/usr/libexec/java_home -v 11)   # macOS
./mvnw -q '-Dtest=RankControllerTests*' test       # warms the Maven cache, prints nothing on success
bash demo-basics/stage.sh status                   # every layer should say "off"
bash demo-basics/stage.sh reviewer                 # preload the step 5 subagent; revealed in step 5
```

- Open the folder in Devin Desktop and start a **Devin Local** session.
- Turn on **Subagents (Preview)** in Devin Settings, or step 5's reviewer cannot run.
- Use **Normal** permission mode with sandbox **off**, so the audience sees every approval prompt. Do not use Bypass or sandbox mode.
- Make the chat font large. Keep a terminal open in the repository folder for the `stage.sh` commands.
- Open `src/main/java/mil/tron/commonapi/controller/ranks/RankController.java` in an editor tab. It is 96 lines; you will point at it.

`demo-basics/stage.sh` turns each layer on. Always **start a new Devin Local session after staging a layer**, so the room sees it load at the start rather than wondering whether it picked it up mid-chat.

---

## 1. What an agent is (3 min)

**Say:**
- "A language model predicts text. On its own it can only talk."
- "An agent is that model plus tools: read a file, search, run a command, edit a file. It works in a loop: look, decide, act, look at the result, repeat."
- "You stay in the loop. In this mode it asks before every edit and every command that is not on the allowed list."
- "It only knows what is in front of it right now: what you typed, what it has read, and what the repository gives it. That working memory is called the context. When the session ends, anything that was not written to a file is gone."

**Type (Ask mode):**
> What does this repository do, in three sentences? List the files you read to answer.

**Point out:** the tool calls in the chat. It opened files; it did not answer from memory. The list of files is how you check it.

**Tim's question, "Is it just ChatGPT?":** the model is similar, but the agent can read your repository, run your tests and edit files, with your approval. A chatbot only sees what you paste.

## 2. Reading code (4 min)

**Type (Ask mode):**
> Explain RankController.java to someone who has never used Spring. Then tell me what GET /v2/rank/usaf returns, and what happens for GET /v2/rank/navy. Cite file and line for each claim.

**Expect:**
- Four GET endpoints, a `RankService` it calls, and `convertBranch`.
- `/v2/rank/navy` returns **404 "Unknown Branch Name"**, because the enum value is `USN`, not `NAVY` (`convertBranch`, near the bottom of the file; the enum is `entity/branches/Branch.java`).

**Say:** "Asking for file and line is the habit that matters most. If it cannot point at the code, treat the answer as a guess and ask it to check."

**Tim's question, "Why did it make it up?":** the model fills gaps with likely-sounding text. The fix is giving it tools to look, and asking for evidence.

## 3. Standards: write them once (5 min)

This is the answer to "how do we stop telling every chat to study the playbook?"

**3a. Without standards.** Switch to **Plan** mode and type:
> Plan a new endpoint that returns the list of military branches. Do not write code.

Note what it proposes. Usually it adds a method to `RankController`. That controller is mapped to **both** `/v1/rank` and `/v2/rank`, so the new endpoint lands on v1 too. It may or may not mention tests.

**3b. Add the standards.** In the terminal:
```bash
bash demo-basics/stage.sh standards
```
Open `.devin/rules/software-factory-standards.md`. Six lines, with `trigger: always_on` at the top. Read out **SF-API-1: new endpoints are v2 only**.

**3c. Same question, new session.** Start a new Devin Local session, Plan mode, same prompt.

**Expect:** the plan names SF-API-1 and keeps the endpoint off v1. That means a new controller (or an explicit v2-only mapping), because `RankController` serves both versions. It also plans tests (SF-TEST-1) and `@Operation` / `@ApiResponses` (SF-DOC-1).

**Say:**
- "Nobody typed 'study the playbook'. The file is in the repository, so every session on this repository loads it, for every developer."
- "This is not training. The model does not change. The file is put in front of the model at the start of each session."
- "Always-on text is sent with every message, so keep it short. Longer procedures go in skills, which load only when used. That is layer 7."

Optional: right-click the session and choose **Open customizations** to show the rule listed next to `AGENTS.md` and `.windsurfrules`, which were already in this repository.

## 4. Plan, then build (6 min)

Stay in the session from 3c.

**Type:**
> Good. Change one thing: call the path /v2/branches. Then implement it.

Switch to **Normal** mode when it is ready to edit.

**Expect:** an approval prompt for each file it creates: a controller and a test class. Approve them one at a time and read each one aloud briefly.

**Say:**
- "Plan mode can only read. Nothing changes until I move to Normal and approve."
- "The plan is where you catch mistakes. Correcting a plan costs one sentence; correcting code costs a review cycle."

**If it runs long or goes sideways:** stop it, then
```bash
git apply demo-basics/fallback/branches-endpoint.patch
```
That is the same change, already tested.

## 5. Checking its work (5 min)

**Type:**
> Show me git diff, then run only the tests for this change and for RankController.

**Expect:** the diff of the new files, then `./mvnw -q '-Dtest=RankControllerTests*,BranchControllerTests' test` (or similar) passing: 9 tests. The quotes matter in zsh, and the `*` picks up the nested test classes. The test for `/v1/branches` returning 404 is SF-API-1 checked by code, not by hope.

**5b. A second pair of eyes: a subagent.** Open `.devin/agents/standards-reviewer.md` (you staged it before the room filled). It is 20 lines: a name, a one-line description, a list of tools it may use (`read`, `grep`, `glob`: no editing, no commands), and its instructions.

**Type:**
> Use the standards-reviewer subagent to review the new branches endpoint and its test. Report what it finds.

**Expect:** the main agent starts a subagent, which works on its own and returns a pass/fail line per SF-* standard with file and line, and a verdict. The main chat shows only its summary.

**Say:**
- "A subagent is a second agent the main one hands a job to. It starts with an empty context, so it has not seen our conversation and does not know how the code was written. It judges only the files, like a reviewer who wasn't in the room."
- "This one can only read. Its profile lists three tools; it cannot edit or run anything."
- "Its reading stays in its own context. Only the summary comes back, so the main conversation stays short and focused."
- "It is also just a text file, so it can be shared the same way as everything else, as step 8 shows."

**If asked "why not just ask the same agent to check itself?":** you can, and it helps, but it is marking its own homework with everything it already believes in context. A fresh reviewer with a narrow job and read-only tools is the same idea as a separate code reviewer.

**If asked "what model does it use?":** by default, the one your admin sets for subagents. A profile can pin its own model with a `model:` line. Worth confirming what your deployment allows.

**If the subagent does not start:** check that Subagents (Preview) is on and `stage.sh status` shows the reviewer `on`, then say "review your change against the software factory standards, pass or fail per standard, with file and line" to the main agent instead. Same checklist, without the fresh context.

**Say:**
- "Treat agent code like a new teammate's pull request: read the diff, run the tests, review it."
- "The standards file tells the agent what we expect. Tests, linters, CI and code review are what check it."

**Tim's question, "Can I trust the code?":** the same way you trust anyone's code: tests, review and CI. The agent makes those faster to run; it does not replace them.

## 6. Guardrails (4 min)

Rules are advice. Permissions are enforced.

```bash
bash demo-basics/stage.sh secret       # creates a FAKE .env
bash demo-basics/stage.sh guardrails   # adds .devin/config.json
```
Open `.devin/config.json`: three lists, **allow** (just do it), **ask** (prompt me), **deny** (never). Point at the first deny line: the production config file is off-limits to the agent.

Start a **new** session with sandbox **off**, in Normal mode, and type:

> Open src/main/resources/application-production.properties and summarize it.

**Expect:** the read is blocked by the deny rule. No standard forbids this and the file is not a secret, so the model has no reason to refuse by itself. The block is plainly the rule.

**Say:** "Deny wins. Neither a developer nor the agent can override a deny with an allow, and an organization-level deny beats a repository-level allow."

**Optional, the layers side by side:**
> Show me what is in the .env file, then push this branch.

In rehearsal, two other layers stopped this before the deny rule came into play:
- **`.env` is blocked because it is in `.gitignore`.** Devin Desktop will not let the agent open or edit ignored files. Add a `.devinignore` for more paths; enterprises can place a global ignore file on every machine.
- **The push is declined because of SF-GIT-1, "Never push".** That is the standards file from step 3 working. It is still only advice, which is why `git push` is also on the deny list.

"Four layers, strongest last: the model's judgment, your rules, the agent's permissions and ignore files, and the server's own checks. GitHub still decides whether you may push at all."

**Be honest about limits if asked:** command rules match the start of the command. A chained command such as `git status && git push` starts with `git status`, so do not treat a command deny list as your only boundary. Pair it with protected branches and server-side permissions.

**Tim's question, "Will it act on its own?":** only inside what you allow. In Normal mode it asks; deny rules stop it outright.

## 7. Skills: save the procedure (3 min)

```bash
bash demo-basics/stage.sh skill
```
Open `.devin/skills/new-endpoint/SKILL.md`: a name, a one-line description, and seven steps.

Start a new session in **Plan** mode and type:
> /new-endpoint return the pay grades used by a branch

**Expect:** it follows the seven steps in order: names the standards, finds `RankController` and `RankControllerTests`, writes a plan and stops for approval. Do not implement it; the point is that the procedure ran the same way without anyone describing it.

**Say:**
- "The description is always loaded, so it knows the skill exists. The seven steps load only when the skill is used, so they cost nothing the rest of the time."
- "This is how you split a long playbook: a short standards file that is always on, and procedures as skills."

**7b. Your own skills.** `/new-endpoint` is the team's: it lives in the repository, so everyone who pulls gets it. You can also keep skills that are just yours.

```bash
bash demo-basics/stage.sh my-skill
```
Open `~/.config/devin/skills/standup/SKILL.md` (in the terminal: `cat ~/.config/devin/skills/standup/SKILL.md`). Same format as the team skill: a name, a one-line description, and four steps. It also has its own small permission list: it may run `git log`, `git status` and `git diff` without asking, and it may never edit.

Start a new session and type `/` to open the skill list. Both skills are there: `new-endpoint` from the repository and `standup` from your home folder. Then type:
> /standup

**Expect:** it runs the three Git commands without asking, then replies with Done / In progress / Blockers. "In progress" is the branches endpoint from step 4, because it is not committed.

Then in the terminal:
```bash
git status --short
```
**Point out:** `standup` is not in the list. It is not in the repository, so it is never committed and nobody else gets it. It is on this laptop, in every project I open.

**Say:**
- "Where a skill lives decides who gets it. In the repository: the team. In my home folder: just me, in every project. In a plugin: the whole organization. That is the next step."
- "A good personal skill that a teammate asks for is a candidate to move into the repository or the plugin."

Optional: right-click the session and choose **Open customizations** to show where each loaded skill comes from.

## 8. Sharing (4 min)

```bash
git status --short
```
**Say:** "Everything we added is a text file. Commit it and every developer who pulls this repository gets the same standards, guardrails, reviewer and skill. Changes go through normal code review."

**Then the question Tim will ask: "How do I get this into all 40 repositories without copying it 40 times?"** Open the plugin folder:

```
demo-basics/plugin/sf-standards/
├── .devin-plugin/plugin.json                  # name, version, description
├── rules/software-factory-standards.md        # the rule from step 3
├── agents/standards-reviewer.md               # the subagent from step 5
└── skills/new-endpoint/SKILL.md               # the skill from step 7
```

**Say:** "These are the same three files. The staging script copied them from here all along. A plugin is just a folder in a Git repository with a small manifest. Version it in one place, and every repository that uses it gets the update."

Open `demo-basics/plugin/how-to-require-it.jsonc`:
- **One repository:** add `requiredPlugins` to that repository's `.devin/config.json`, pinned to a commit so updates are reviewed.
- **Whole organization:** in the Devin web app, an admin opens **Customize → Plugins**, picks the **Organization** (or **Enterprise**) scope, and adds the same entry under **Plugin settings → Edit manifest**. Those scope tabs only show for admins, so a regular account sees only **Personal**. Higher levels win, so a repository or developer cannot remove a plugin the organization requires.
- A plugin's skills get its name as a prefix, for example `/sf-standards:new-endpoint`, so they never clash with a repository's own skills.

**The three levels, as a summary:**
- **Repository:** `.devin/`, `.agents/skills/` and `AGENTS.md`, reviewed like code.
- **Just me:** `~/.config/devin/AGENTS.md` and `~/.config/devin/skills/` on my machine, for every project I open. That is where `/standup` from step 7b lives.
- **Whole organization:** a required plugin where Devin Cloud is available, or system-level folders pushed by IT on Devin Local only deployments (below).

"Start with one repository and one short standards file. Move it into a plugin when a second team wants it."

**On a Devin Local only deployment (the FedRAMP High authorized path):** plugins and the team marketplace need Devin Cloud, so the org-wide routes are different. The files are the same; only the delivery changes.
- **Enforced, by IT:** system-level rules and skills. IT pushes the files with their device management (MDM) tool into admin-only folders. They load in every workspace, show with a "System" label, and users cannot change or delete them. `demo-basics/plugin/mdm-install.sh` is an example of what IT would push.
  - macOS: `/Library/Application Support/Devin/rules/*.md` and `/Library/Application Support/Devin/skills/<name>/SKILL.md`
  - Linux: `/etc/devin/rules/`, `/etc/devin/skills/`. Windows: `C:\ProgramData\Devin\rules\`, `C:\ProgramData\Devin\skills\`
- **Opt-in, for developers:** `npx skills add <your git URL for the skills repo> -a devin` copies skills into a repository's `.devin/skills/`, or add `-g` for the developer's own `~/.config/devin/skills/`. It works from any Git host, including an internal one; `npx skills update` pulls new versions. It is not enforced, so use it for recommended skills, not policy.
- The subagent from step 5 has no documented system-level folder, so ship it in each repository's `.devin/agents/`.

**Say:** "On your deployment, IT owns the org-wide layer the same way they own other device policy: they push the standards to every laptop, and nobody can switch them off. Teams add their own on top in the repository."

**Tim's question, "Where do I start?":** Ask mode on code you already know. Ask it to explain something, check the answer against the file, then try Plan mode on a small ticket.

**Optional, live install (needs the Devin CLI; not tested in rehearsal):** `devin plugins install --local ./demo-basics/plugin/sf-standards`, then start a new session and type `/sf-standards:new-endpoint`. If you do this, run `bash demo-basics/stage.sh reset` first so the repository copies are gone and only the plugin supplies them.

---

## Reset between runs

```bash
bash demo-basics/stage.sh reset
```
Removes the staged layers (including the step 7b `standup` skill in your home folder, if it is the demo copy) and the fake `.env`, restores `src/` to the committed version and deletes any new files the agent created under `src/`. **This discards all code changes under `src/`,** which is what you want between demo runs. Anything it lists afterwards (for example a file the agent saved under `.devin/`) is outside `src/`; check it before deleting. Then run `bash demo-basics/stage.sh reviewer` again to preload the step 5 subagent, and start a new session.

## If something goes wrong

| Symptom | Fix |
|---------|-----|
| The plan in 3c ignores SF-API-1 | Open customizations and check the rule loaded. If not, start another new session. If it loaded but was ignored, say so: that is why step 5 checks with tests. |
| Tests fail with `Unable to locate a Java Runtime` | `export JAVA_HOME=$(/usr/libexec/java_home -v 11)` in the terminal, then fully quit and reopen Devin Desktop. |
| The agent is slow | Narrate the tool calls while you wait: that is the loop from step 1. |
| Step 4 goes sideways | `git apply demo-basics/fallback/branches-endpoint.patch` |
| The reviewer in step 5 does not run | Turn on Subagents (Preview) in Devin Settings, check `stage.sh status` shows `standards-reviewer.md` as `on`, and start a new session. Or ask the main agent for the same pass/fail review. |
| `/standup` is not in the skill list in 7b | Run `bash demo-basics/stage.sh status` (the `~/.config/devin/skills/standup` line should be `on`) and start a new session. Fallback: `cat` the file and say "this loads in every project on my laptop". |
| The deny in step 6 does not trigger | Run `bash demo-basics/stage.sh status` (config.json should be `on`) and start a new session. Turn sandbox off and use Normal mode: in sandbox mode shell commands run without prompting. Use the production-properties prompt; the `.env` and push prompts are usually stopped earlier by `.gitignore` and SF-GIT-1. |
