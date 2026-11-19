#!/usr/bin/env python3
"""Checks that a release on Maven Central and its GitHub Release hold the same files.

The GitHub Release is built by CI from the tag, with build provenance; Maven Central gets the maintainer's
own build of the same tag. Both builds are reproducible, so every jar and the BOM must be byte for byte the
same in both places. This downloads every file of the GitHub Release and the file of the same name from
Maven Central, and compares their SHA-256. It fails when a file differs or is missing on Maven Central.

Usage: scripts/check-release-checksums.py X.Y.Z

GITHUB_REPOSITORY and MAVEN_REPOSITORY override the defaults below; GITHUB_TOKEN, when set, is sent to the
GitHub API to avoid its rate limit for anonymous calls.
"""

import hashlib
import json
import os
import sys
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

GITHUB_REPOSITORY = os.environ.get("GITHUB_REPOSITORY", "poppycoderr/domain-driven-kit")
MAVEN_REPOSITORY = os.environ.get("MAVEN_REPOSITORY", "https://repo.maven.apache.org/maven2")
GROUP_PATH = "com/domaindrivenkit"


def fetch(url, headers=None):
    request = urllib.request.Request(url, headers={"User-Agent": "ddk-release-check", **(headers or {})})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def assets(version):
    headers = {"Accept": "application/vnd.github+json"}
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        headers["Authorization"] = "Bearer " + token
    url = f"https://api.github.com/repos/{GITHUB_REPOSITORY}/releases/tags/v{version}"
    release = json.loads(fetch(url, headers))
    return [(asset["name"], asset["browser_download_url"]) for asset in release["assets"]]


def central_url(name, version):
    """ddk-core-1.0.1-sources.jar -> .../com/domaindrivenkit/ddk-core/1.0.1/ddk-core-1.0.1-sources.jar"""
    marker = f"-{version}"
    if marker not in name:
        raise ValueError(f"{name} does not carry version {version}")
    artifact = name[: name.index(marker)]
    return f"{MAVEN_REPOSITORY}/{GROUP_PATH}/{artifact}/{version}/{name}"


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def main():
    if len(sys.argv) != 2:
        print("usage: scripts/check-release-checksums.py X.Y.Z", file=sys.stderr)
        return 2
    version = sys.argv[1].removeprefix("v")
    files = assets(version)
    if not files:
        print(f"FAIL the GitHub Release v{version} has no files", file=sys.stderr)
        return 1
    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(lambda file: compare(*file, version), sorted(files)))
    for line in results:
        print(line)
    failed = sum(1 for line in results if line.startswith("FAIL"))
    print(f"{len(files) - failed} of {len(files)} files match")
    return 1 if failed else 0


def compare(name, url, version):
    github = sha256(fetch(url))
    try:
        central = sha256(fetch(central_url(name, version)))
    except urllib.error.HTTPError as e:
        return f"FAIL {name}: not on Maven Central ({e.code})"
    if github != central:
        return f"FAIL {name}: GitHub Release {github}, Maven Central {central}"
    return f"ok   {name} {github}"


if __name__ == "__main__":
    sys.exit(main())
