# Coding-agent evaluation set

English | [简体中文](./README.zh-CN.md)

DDK's generated projects are written for coding agents as much as for people: `AGENTS.md`, the skills under `.claude/skills/` and an `ArchitectureTest` whose failures say how to fix the code. This set measures whether that works. Each task gives an agent a generated project and a prompt, then grades the result with tests the agent never saw.

It changes no library code and is not part of a release. CI checks only that the graders are sound; running an agent costs money and needs its credentials, so the maintainer runs it by hand, for example before a release that changes the archetypes.

## Tasks

| Task | Archetype | What it checks |
|---|---|---|
| `layer4-place-order` | four-layer | A new aggregate end to end: placement, lookup and cancellation, error codes, validation, and a domain event that carries the new order's ID |
| `layer4-cancel-reason` | four-layer, starting from the order | A change to an existing aggregate and its table: an optional cancel reason with a length rule, through a new migration rather than an edit of the one that ran |
| `layer4-status-shortcut` | four-layer, starting from the order | A small read endpoint whose prompt suggests calling the repository from the controller. The architecture rules forbid it, so the agent has to say no to the prompt |
| `layer3-place-order` | three-layer | The `layer4-place-order` contract on the three-layer archetype |

A task directory holds:

- `task.json`: the archetype, the task it starts from (`seed`), and other tasks whose graders must still pass (`also_grade`);
- `prompt.md`: what the agent gets, in the language of the generated project;
- `grader/`: hidden tests, copied into `src/test/java/com/acme/order/eval/` after the agent has finished. They test the HTTP contract and the few type names that the prompt states;
- `reference/`: a solution, laid over the generated project. It is how CI knows the grader can be passed, and the starting point of the tasks that name it as their seed.

## How a run is graded

```text
generate the project from the archetype at this checkout's version
lay the seed task's reference over it, git init, commit
record the protected files: ArchitectureTest.java and every migration that already exists
run the agent (prompt on stdin, the project as working directory), or lay the reference over it
agent exits non-zero or times out -> fail at "agent"
protected file changed          -> fail at "protected"
copy the graders, mvn verify    -> fail at "compile" or "verify", otherwise pass
```

The results split the surefire reports into the architecture test, the grader and the project's own tests, so a failure says whether the agent broke a layering rule or the contract.

## Running it

DDK has to be installed in the local Maven repository at the version of this checkout:

```bash
mvn -B -ntp install -DskipTests
```

Check the graders, as CI does. Every task must fail on the bare project and pass with its reference:

```bash
scripts/run-agent-eval.py --check
```

Run an agent. The command gets the prompt on stdin and runs in the generated project. In print mode Claude Code asks for no permissions, so name the commands it may run, `mvn` above all, or it cannot check its own work:

```bash
scripts/run-agent-eval.py --agent "claude -p --permission-mode acceptEdits --allowedTools 'Bash(mvn *)' 'Bash(ls *)' 'Bash(cat *)' 'Bash(find *)' 'Bash(grep *)' 'Bash(git status*)' 'Bash(git diff*)'"
```

```bash
scripts/run-agent-eval.py --agent 'codex exec -s workspace-write -' --task layer4-status-shortcut
```

Projects are generated in a new temporary directory outside this repository, so that the agent reads the generated project's `AGENTS.md` and not DDK's own. `--work DIR` puts them elsewhere. Results go to `target/agent-eval/<time>/`: `results.md`, `results.json`, and per task the agent's output, the build log, the diff the agent made and the ArchGuard report.

Agents are not deterministic. Run each task several times before drawing a conclusion from one failure, and read the diff: a pass that copies a lot of code into the controller, or a failure caused by a name the prompt did not make clear, says more than the count.

## Adding a task

Write the prompt as a user would, and state every name the grader compiles against. Grade through the HTTP contract where you can, so that the agent is free to choose its own internal design. Write the reference, then run `scripts/run-agent-eval.py --check --task <id>`: the bare project has to fail and the reference has to pass. Before you add the task, check that the grader also rejects the mistake the task is about, for example with a small script passed as `--agent` that applies the reference with the mistake.
