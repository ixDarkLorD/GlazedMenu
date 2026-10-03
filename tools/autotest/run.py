#!/usr/bin/env python3
"""Glazed Menu's client smoke test, across every Minecraft version, oldest first.

For each version folder (a git worktree of this repo, beside this one) and each of its loaders, it starts the dev
client with DevAutotest copied in, which presses the title screen's Mods button, checks Glazed Menu's mod list opens,
opens the config screens and quits. The sources are put back afterwards, whatever happens.

    python tools/autotest/run.py                  every version, every loader
    python tools/autotest/run.py 1.21 26.3        only these versions
    python tools/autotest/run.py --loader fabric  only this loader

Screenshots and logs land in tools/autotest/results/<version>/<loader>/.
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
MODS = HERE.parents[2]
# Oldest first: the folder of each version's worktree.
VERSIONS = {
    "1.20": "GlazedMenu-1.20",
    "1.21": "GlazedMenu-1.21",
    "26.1": "GlazedMenu-26.1.2",
    "26.2": "GlazedMenu-26.2",
    "26.3": "GlazedMenu-26.3",
}
CORE = "common/src/main/java/net/ixdarklord/glazedmenu/internal/core"
HOOK = "    public static void tick(Minecraft minecraft) {"
OPTIONS = "onboardAccessibility:false\ntutorialStep:none\nskipMultiplayerWarning:true\nguiScale:2\n"
TIMEOUT = 420


def inject(root: Path):
    """Copies the test in and calls it from the client tick; returns how to undo that."""
    test = root / CORE / "DevAutotest.java"
    constructor = root / CORE / "GlazedClientConstructor.java"
    original = constructor.read_bytes()
    text = original.decode("utf-8")
    if HOOK not in text:
        raise RuntimeError(f"no client tick to hook in {constructor}")
    newline = "\r\n" if "\r\n" in text else "\n"
    constructor.write_bytes(text.replace(HOOK, HOOK + newline + "        DevAutotest.tick(minecraft);", 1).encode("utf-8"))
    shutil.copyfile(HERE / "DevAutotest.java", test)

    def restore():
        constructor.write_bytes(original)
        test.unlink(missing_ok=True)

    return restore


def run_loader(root: Path, version: str, loader: str) -> dict:
    run_dir = root / "run"
    shots = run_dir / "screenshots"
    run_dir.mkdir(exist_ok=True)
    if not (run_dir / "options.txt").exists():
        (run_dir / "options.txt").write_text(OPTIONS)
    if shots.exists():
        for old in shots.glob("autotest_*.png"):
            old.unlink()
    out = HERE / "results" / version / loader
    shutil.rmtree(out, ignore_errors=True)
    out.mkdir(parents=True)

    env = dict(os.environ, JAVA_TOOL_OPTIONS="-Dglazedmenu.autotest=true")
    gradlew = str(root / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    started = time.time()
    log = out / "run.log"
    timed_out = False
    with open(log, "wb") as sink:
        # Offline: nothing to download once a version has been built, and it skips Gradle's network checks.
        process = subprocess.Popen([gradlew, f":{loader}:runClient", "--offline", "-q"], cwd=root, env=env, stdout=sink, stderr=subprocess.STDOUT)
        try:
            process.wait(timeout=TIMEOUT)
        except subprocess.TimeoutExpired:
            timed_out = True
            kill_tree(process.pid)
    seconds = time.time() - started

    text = log.read_text(encoding="utf-8", errors="replace")
    checks = re.findall(r"AUTOTEST (PASS|FAIL) (\S+)", text)
    result = re.search(r"AUTOTEST RESULT passed=(\d+) failed=(\d+)", text)
    images = sorted(shots.glob("autotest_*.png")) if shots.exists() else []
    for image in images:
        shutil.copyfile(image, out / image.name)
    failures = [name for state, name in checks if state == "FAIL"]
    if timed_out:
        failures.append(f"timed out after {TIMEOUT}s")
    elif result is None:
        failures.append("the game ended before the test finished: " + first_error(text))
    return {"version": version, "loader": loader, "ok": not failures, "checks": checks, "failures": failures,
            "seconds": seconds, "shots": len(images), "log": log}


def first_error(text: str) -> str:
    for pattern in (r"error: .*", r"Caused by: .*", r"\S*Exception\S*: .*", r"What went wrong:\s*\n.*"):
        match = re.search(pattern, text)
        if match:
            return " ".join(match.group(0).split())[:200]
    return "no error found in the log"


def kill_tree(pid: int):
    if os.name == "nt":
        subprocess.run(["taskkill", "/F", "/T", "/PID", str(pid)], capture_output=True)
    else:
        subprocess.run(["pkill", "-P", str(pid)], capture_output=True)


def loaders_of(root: Path) -> list:
    properties = (root / "gradle.properties").read_text(encoding="utf-8")
    return re.search(r"^project_loaders=(.*)$", properties, re.M).group(1).strip().split(",")


def main() -> int:
    parser = argparse.ArgumentParser(description="Runs Glazed Menu's client smoke test on every version, oldest first.")
    parser.add_argument("versions", nargs="*", help=f"versions to run (default: all of {', '.join(VERSIONS)})")
    parser.add_argument("--loader", action="append", help="only this loader (may be repeated)")
    arguments = parser.parse_args()
    unknown = [v for v in arguments.versions if v not in VERSIONS]
    if unknown:
        parser.error(f"unknown version(s) {unknown}; known: {', '.join(VERSIONS)}")

    results = []
    for version, folder in VERSIONS.items():
        if arguments.versions and version not in arguments.versions:
            continue
        root = MODS / folder
        if not root.is_dir():
            print(f"[{version}] skipped: {root} isn't there")
            continue
        restore = inject(root)
        try:
            for loader in loaders_of(root):
                if arguments.loader and loader not in arguments.loader:
                    continue
                print(f"[{version}] {loader} ...", flush=True)
                result = run_loader(root, version, loader)
                results.append(result)
                state = "PASS" if result["ok"] else "FAIL"
                passed = sum(1 for s, _ in result["checks"] if s == "PASS")
                print(f"[{version}] {loader}: {state}  {passed}/{len(result['checks'])} checks, {result['shots']} screenshots, {result['seconds']:.0f}s")
                for failure in result["failures"]:
                    print(f"    - {failure}")
        finally:
            restore()

    print()
    print(f"{'version':8} {'loader':9} {'result':6} {'checks':7} {'time':>5}")
    for r in results:
        passed = sum(1 for s, _ in r["checks"] if s == "PASS")
        print(f"{r['version']:8} {r['loader']:9} {'PASS' if r['ok'] else 'FAIL':6} {passed}/{len(r['checks']):<5} {r['seconds']:4.0f}s")
    bad = [r for r in results if not r["ok"]]
    print(f"\n{len(results) - len(bad)} of {len(results)} runs passed. Results: {HERE / 'results'}")
    return 1 if bad or not results else 0


if __name__ == "__main__":
    sys.exit(main())
