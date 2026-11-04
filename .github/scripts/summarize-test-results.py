#!/usr/bin/env python3

import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MINIMUM_LINE_COVERAGE = 50.0
TARGET_LINE_COVERAGE = 90.0
REQUIRED_DOCKER_TESTS = {
    "io.github.rroyoo.mockjdbc.proxy.KafkaMockedQueryEventProducerIntegrationTest",
    "io.github.rroyoo.mockjdbc.wiremock.KafkaMappingConsumerIntegrationTest",
}


def main():
    test_suites = []
    for report in sorted(ROOT.rglob("target/surefire-reports/TEST-*.xml")):
        suite_root = ET.parse(report).getroot()
        test_suites.extend(suite_root.iter("testsuite"))

    tests = sum(int(suite.get("tests", 0)) for suite in test_suites)
    failures = sum(int(suite.get("failures", 0)) for suite in test_suites)
    errors = sum(int(suite.get("errors", 0)) for suite in test_suites)
    skipped = sum(int(suite.get("skipped", 0)) for suite in test_suites)
    test_suites_by_name = {suite.get("name"): suite for suite in test_suites}
    docker_test_results = {}
    for suite_name in REQUIRED_DOCKER_TESTS:
        suite = test_suites_by_name.get(suite_name)
        if suite is None:
            result = "not found"
        elif (
            int(suite.get("tests", 0)) > 0
            and int(suite.get("skipped", 0)) == 0
            and int(suite.get("failures", 0)) == 0
            and int(suite.get("errors", 0)) == 0
        ):
            result = "passed"
        else:
            result = "failed or skipped"
        docker_test_results[suite_name] = result

    covered_lines = 0
    missed_lines = 0
    coverage_reports = sorted(ROOT.rglob("target/site/jacoco/jacoco.xml"))
    for report in coverage_reports:
        report_root = ET.parse(report).getroot()
        line_counter = next(
            (counter for counter in report_root.findall("counter")
             if counter.get("type") == "LINE"),
            None,
        )
        if line_counter is not None:
            covered_lines += int(line_counter.get("covered", 0))
            missed_lines += int(line_counter.get("missed", 0))

    total_lines = covered_lines + missed_lines
    coverage = (covered_lines / total_lines * 100) if total_lines else 0.0
    report_lines = [
        "## CI quality summary",
        "",
        "| Signal | Result |",
        "| --- | ---: |",
        f"| Tests | {tests} |",
        f"| Failures | {failures} |",
        f"| Errors | {errors} |",
        f"| Skipped | {skipped} |",
        f"| JaCoCo XML reports | {len(coverage_reports)} |",
        f"| Line coverage | {coverage:.2f}% ({covered_lines}/{total_lines}) |",
        f"| Required line coverage | {MINIMUM_LINE_COVERAGE:.0f}% |",
        f"| Line coverage target | {TARGET_LINE_COVERAGE:.0f}% |",
        "| Required Docker tests | " + ", ".join(
            f"`{suite_name}`: {result}"
            for suite_name, result in sorted(docker_test_results.items())
        ) + " |",
        "",
        "Coverage is combined from each Maven module and the standalone sample.",
        "Detailed static-analysis findings are published in the repository Security tab.",
        "",
    ]

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as summary:
            summary.write("\n".join(report_lines))
    else:
        print("\n".join(report_lines))

    if not test_suites:
        print("No Surefire test reports were found.", file=sys.stderr)
        return 1
    if not coverage_reports or total_lines == 0:
        print("No JaCoCo line coverage reports were found.", file=sys.stderr)
        return 1
    if failures or errors:
        print(f"Tests reported {failures} failures and {errors} errors.", file=sys.stderr)
        return 1
    if any(result != "passed" for result in docker_test_results.values()):
        print(
            "Required Docker-backed Kafka integration tests must be found and pass without skips.",
            file=sys.stderr,
        )
        return 1
    if coverage < MINIMUM_LINE_COVERAGE:
        print(
            f"Line coverage {coverage:.2f}% is below the required "
            f"{MINIMUM_LINE_COVERAGE:.0f}%.",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
