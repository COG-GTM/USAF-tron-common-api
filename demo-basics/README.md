# AFLCMC 201: agents from first principles

A 30-minute live demo for developers who have not used an AI agent before. It uses one small, real endpoint in this repository and adds one idea at a time. Each layer is a file you can open, so nothing is hidden.

| # | Layer | The one idea | Minutes |
|---|-------|--------------|---------|
| 1 | What an agent is | A model that can use tools in a loop, with you approving | 3 |
| 2 | Reading code | It reads the real files, and you ask where each answer came from | 4 |
| 3 | Standards | Write the standard once in a file; every session loads it | 5 |
| 4 | Plan, then build | Plan first, approve each edit | 6 |
| 5 | Checking its work | Read the diff, run the test, ask it to check the standards | 4 |
| 6 | Guardrails | Some things it is never allowed to do, whatever it is told | 3 |
| 7 | Skills | Save a good procedure once; anyone reruns it with one command | 3 |
| 8 | Sharing | Repo, user, org: the same files at three levels | 2 |

Everything is synthetic and offline. The only secret in the demo is a fake `.env` file that says it is fake.

The deeper demo (MCP, hooks, subagents, prompt injection, org plugins) is on the `demo/aflcmc-201` branch. Use it for follow-up questions or a 301.

## Before the room fills (10 minutes)

```bash
git clone https://github.com/COG-GTM/USAF-tron-common-api.git
cd USAF-tron-common-api
git checkout demo/aflcmc-201-basics
export JAVA_HOME=$(/usr/libexec/java_home -v 11)   # macOS
./mvnw -q '-Dtest=RankControllerTests*' test       # warms the Maven cache, prints nothing on success
bash demo-basics/stage.sh status                   # every layer should say "off"
```

- Open the folder in Devin Desktop and start a **Devin Local** session.
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

## 5. Checking its work (4 min)

**Type:**
> Show me git diff, then run only the tests for this change and for RankController.

**Expect:** the diff of the new files, then `./mvnw -q '-Dtest=RankControllerTests*,BranchControllerTests' test` (or similar) passing: 9 tests. The quotes matter in zsh, and the `*` picks up the nested test classes. The test for `/v1/branches` returning 404 is SF-API-1 checked by code, not by hope.

Then:
> Review your change against the software factory standards. For each standard say pass or fail, with file and line.

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

## 8. Sharing (2 min)

```bash
git status --short
```
**Say:**
- "Everything we added is a text file. Commit it and every developer who pulls the repository gets the same standards, guardrails and skills. Changes go through normal code review."
- "Three levels:
  - **Repository**: `.devin/` and `AGENTS.md`, as today.
  - **Just me**: the same files under `~/.devin/` on my machine, for every project I open.
  - **Whole organization**: a plugin that bundles rules, skills and permissions, which an admin can make required. Confirm with your admin what is enabled on your deployment."
- "Start with one repository and one short standards file. Grow it when you see the agent repeat a mistake."

**Tim's question, "Where do I start?":** Ask mode on code you already know. Ask it to explain something, check the answer against the file, then try Plan mode on a small ticket.

---

## Reset between runs

```bash
bash demo-basics/stage.sh reset
```
Removes the staged layers, the fake `.env`, the new branches endpoint and its test, and restores `src/` to the committed version. **This discards any code changes under `src/`,** which is what you want between demo runs. Then start a new session.

## If something goes wrong

| Symptom | Fix |
|---------|-----|
| The plan in 3c ignores SF-API-1 | Open customizations and check the rule loaded. If not, start another new session. If it loaded but was ignored, say so: that is why step 5 checks with tests. |
| Tests fail with `Unable to locate a Java Runtime` | `export JAVA_HOME=$(/usr/libexec/java_home -v 11)` in the terminal, then fully quit and reopen Devin Desktop. |
| The agent is slow | Narrate the tool calls while you wait: that is the loop from step 1. |
| Step 4 goes sideways | `git apply demo-basics/fallback/branches-endpoint.patch` |
| The deny in step 6 does not trigger | Run `bash demo-basics/stage.sh status` (config.json should be `on`) and start a new session. Turn sandbox off and use Normal mode: in sandbox mode shell commands run without prompting. Use the production-properties prompt; the `.env` and push prompts are usually stopped earlier by `.gitignore` and SF-GIT-1. |
