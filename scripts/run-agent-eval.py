#!/usr/bin/env python3
"""Runs the coding-agent evaluation set in evals/tasks.

Each task generates a project from a DDK archetype, lets a coding agent (or the task's reference solution) work in it,
then copies in hidden grader tests and runs mvn verify. A task passes when the files the agent must not touch are
unchanged, the build passes, and the grader tests ran. An agent that exits with an error or times out fails at the
"agent" stage, so a run that never started is not mistaken for code that does not compile. See evals/README.md.

  scripts/run-agent-eval.py --agent 'claude -p --permission-mode acceptEdits'   # prompt on stdin, cwd is the project
  scripts/run-agent-eval.py --reference --task layer4-place-order
  scripts/run-agent-eval.py --check   # CI: graders fail on the bare project and pass on the reference

DDK must be installed in the local Maven repository at the version of this checkout (mvn install).
"""

import argparse
import datetime
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TASKS = ROOT / "evals" / "tasks"
POM_NS = {"m": "http://maven.apache.org/POM/4.0.0"}
GROUP_ID = "com.acme"
ARTIFACT_ID = "order-service"
PACKAGE = "com.acme.order"
GRADER_PACKAGE_DIR = Path("src/test/java/com/acme/order/eval")
ARCHITECTURE_TEST = PACKAGE + ".ArchitectureTest"


def root_pom_value(path):
    element = ET.parse(ROOT / "pom.xml").getroot().find(path, POM_NS)
    if element is None or not element.text:
        sys.exit("pom.xml has no " + path)
    return element.text.strip()


def load_task(task_id):
    task_dir = TASKS / task_id
    if not (task_dir / "task.json").is_file():
        sys.exit("unknown task: " + task_id)
    task = json.loads((task_dir / "task.json").read_text(encoding="utf-8"))
    task["id"] = task_id
    task["dir"] = task_dir
    return task


def overlay(source, target):
    for path in sorted(source.rglob("*")):
        if path.is_file():
            destination = target / path.relative_to(source)
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, destination)


def apply_seed(task, project):
    """A seed task's reference is the starting point, applied recursively so that seeds can build on each other."""
    seed = task.get("seed")
    if seed:
        seed_task = load_task(seed)
        apply_seed(seed_task, project)
        overlay(seed_task["dir"] / "reference", project)


def run(command, cwd, log, timeout=None, stdin=None, shell=False):
    with open(log, "ab") as out:
        out.write(("$ " + (command if shell else " ".join(command)) + "\n").encode())
        out.flush()
        try:
            return subprocess.run(command, cwd=cwd, stdout=out, stderr=subprocess.STDOUT, timeout=timeout, input=stdin, shell=shell).returncode
        except subprocess.TimeoutExpired:
            out.write(b"\n[timed out]\n")
            return None


def protected_files(task, project):
    """The architecture test and every migration that exists before the agent starts, plus any the task lists."""
    paths = [p for p in project.rglob("ArchitectureTest.java")]
    paths += sorted((project / "src/main/resources/db/migration").glob("*.sql"))
    paths += [project / p for p in task.get("protected", [])]
    return {str(p.relative_to(project)): digest(p) for p in paths}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None


def read_reports(project):
    """Groups surefire results into the architecture test, the hidden grader and the project's own tests."""
    groups = {"architecture": [0, 0], "grader": [0, 0], "project": [0, 0]}
    for report in sorted((project / "target/surefire-reports").glob("TEST-*.xml")):
        suite = ET.parse(report).getroot()
        name = suite.get("name", "")
        group = "architecture" if name == ARCHITECTURE_TEST else "grader" if name.startswith(PACKAGE + ".eval.") else "project"
        total = int(suite.get("tests", 0))
        bad = int(suite.get("failures", 0)) + int(suite.get("errors", 0)) + int(suite.get("skipped", 0))
        groups[group][0] += total - bad
        groups[group][1] += total
    return groups


def run_task(task, mode, args, work, results_dir):
    started = time.monotonic()
    task_work = work / (task["id"] + "-" + mode)
    task_work.mkdir(parents=True)
    out = results_dir / (task["id"] + "-" + mode)
    out.mkdir(parents=True)
    log = out / "build.log"
    project = task_work / ARTIFACT_ID
    result = {"task": task["id"], "mode": mode, "passed": False, "stage": "generate"}

    def finish(stage):
        result["stage"] = stage
        result["seconds"] = round(time.monotonic() - started)
        return result

    generate = [args.mvn, "-B", "-ntp", "-q", "org.apache.maven.plugins:maven-archetype-plugin:" + args.archetype_plugin + ":generate",
                "-DarchetypeGroupId=com.domaindrivenkit", "-DarchetypeArtifactId=" + task["archetype"], "-DarchetypeVersion=" + args.ddk_version,
                "-DgroupId=" + GROUP_ID, "-DartifactId=" + ARTIFACT_ID, "-Dversion=0.0.1-SNAPSHOT", "-Dpackage=" + PACKAGE, "-DinteractiveMode=false"]
    if run(generate, task_work, log) != 0:
        return finish("generate")
    apply_seed(task, project)
    git = ["git", "-c", "user.name=ddk-eval", "-c", "user.email=ddk-eval@example.com"]
    run(["git", "init", "-q"], project, log)
    (project / ".git/info/exclude").write_text("target/\n", encoding="utf-8")
    run(["git", "add", "-A"], project, log)
    run(git + ["commit", "-q", "-m", "seed"], project, log)
    protected = protected_files(task, project)

    if mode == "agent":
        prompt = (task["dir"] / "prompt.md").read_bytes()
        code = run(args.agent, project, out / "agent.log", timeout=args.timeout, stdin=prompt, shell=True)
        result["agent_exit"] = code
        if code != 0:
            return finish("agent")
    elif mode == "reference":
        overlay(task["dir"] / "reference", project)

    run(["git", "add", "-A"], project, log)
    with open(out / "changes.diff", "wb") as diff:
        subprocess.run(["git", "diff", "--cached", "--stat", "--patch"], cwd=project, stdout=diff)

    changed = sorted(p for p, h in protected.items() if digest(project / p) != h)
    if changed:
        result["protected_changed"] = changed
        return finish("protected")

    shutil.rmtree(project / GRADER_PACKAGE_DIR, ignore_errors=True)
    shutil.rmtree(project / "target", ignore_errors=True)
    for graded in [task["id"]] + task.get("also_grade", []):
        overlay(TASKS / graded / "grader", project)
    build = run([args.mvn, "-B", "-ntp", "verify"], project, log)
    groups = read_reports(project)
    result.update({k: "%d/%d" % tuple(v) for k, v in groups.items()})
    violations = project / "target/archguard/violations.md"
    if violations.is_file():
        shutil.copy2(violations, out / "violations.md")
    if groups["grader"][1] == 0:
        return finish("compile" if build != 0 else "grader")
    if build != 0:
        return finish("verify")
    result["passed"] = True
    return finish("passed")


def write_results(results, results_dir):
    (results_dir / "results.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    lines = ["| Task | Mode | Result | Stage | Architecture | Grader | Project tests | Seconds |", "|---|---|---|---|---|---|---|---|"]
    for r in results:
        lines.append("| %s | %s | %s | %s | %s | %s | %s | %s |" % (
            r["task"], r["mode"], "pass" if r["passed"] else "fail", r["stage"], r.get("architecture", "-"), r.get("grader", "-"),
            r.get("project", "-"), r.get("seconds", "-")))
    table = "\n".join(lines) + "\n"
    (results_dir / "results.md").write_text(table, encoding="utf-8")
    print(table)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    how = parser.add_mutually_exclusive_group(required=True)
    how.add_argument("--agent", help="shell command that runs the agent; it gets the prompt on stdin and the project as its working directory")
    how.add_argument("--reference", action="store_true", help="apply each task's reference solution instead of running an agent")
    how.add_argument("--check", action="store_true", help="check the graders: they must fail on the bare project and pass on the reference")
    parser.add_argument("--task", action="append", help="task to run (repeatable); all tasks by default")
    parser.add_argument("--ddk-version", default=root_pom_value("m:version"), help="DDK version to generate from (default: this checkout)")
    parser.add_argument("--archetype-plugin", default=root_pom_value("m:properties/m:maven-archetype.version"))
    parser.add_argument("--timeout", type=int, default=1800, help="seconds an agent may run per task")
    parser.add_argument("--mvn", default="mvn")
    parser.add_argument("--work", type=Path, help="where projects are generated; a fresh temporary directory outside this repository by default, "
                                                  "so that the agent does not read DDK's own AGENTS.md")
    args = parser.parse_args()

    task_ids = args.task or sorted(p.name for p in TASKS.iterdir() if (p / "task.json").is_file())
    tasks = [load_task(t) for t in task_ids]
    work = args.work or Path(tempfile.mkdtemp(prefix="ddk-agent-eval-"))
    if args.work and args.work.exists() and any(args.work.iterdir()):
        sys.exit("--work must be a new or empty directory: " + str(args.work))
    results_dir = ROOT / "target" / "agent-eval" / datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    results_dir.mkdir(parents=True)
    print("projects in %s, results in %s" % (work, results_dir), flush=True)

    modes = ["bare", "reference"] if args.check else ["reference"] if args.reference else ["agent"]
    results = []
    for task in tasks:
        for mode in modes:
            result = run_task(task, mode, args, work, results_dir)
            print("%s %s: %s (%s)" % (task["id"], mode, "pass" if result["passed"] else "fail", result["stage"]), flush=True)
            results.append(result)
    write_results(results, results_dir)

    if args.check:
        wrong = [r for r in results if r["passed"] != (r["mode"] == "reference")]
        for r in wrong:
            print("grader check failed: %s %s %s" % (r["task"], r["mode"], "passed" if r["passed"] else "failed at " + r["stage"]))
        return 1 if wrong else 0
    return 0 if all(r["passed"] for r in results) else 1


if __name__ == "__main__":
    sys.exit(main())
