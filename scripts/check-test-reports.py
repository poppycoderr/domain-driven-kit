#!/usr/bin/env python3
"""Fails when a test was skipped, so CI cannot turn green with tests that never ran.

Reads every Surefire report under the given directory, including those of the projects that the
archetype integration tests generate. Container tests skip themselves when Docker is missing;
CI switches that condition off so they fail instead, and this check catches every other skip
(@Disabled, assumptions, conditions added later).

Usage: scripts/check-test-reports.py [directory]
"""

import sys
from pathlib import Path
from xml.etree import ElementTree


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    reports = sorted(p for p in root.glob("**/target/surefire-reports/TEST-*.xml"))
    total, skipped = 0, []
    for report in reports:
        suite = ElementTree.parse(report).getroot()
        total += int(suite.get("tests", 0))
        cases = [f"{c.get('classname')}#{c.get('name')}" for c in suite.iter("testcase") if c.find("skipped") is not None]
        skipped += cases or ([f"{suite.get('name')} (whole class)"] if int(suite.get("skipped", 0)) else [])
    if not reports or total == 0:
        print(f"No test results under {root.resolve()}")
        return 1
    if skipped:
        print(f"{len(skipped)} skipped of {total} tests in {len(reports)} reports:")
        for name in skipped:
            print(f"  - {name}")
        return 1
    print(f"{total} tests in {len(reports)} reports, none skipped")
    return 0


if __name__ == "__main__":
    sys.exit(main())
