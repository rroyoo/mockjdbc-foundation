import contextlib
import importlib.util
import io
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SUMMARY_SCRIPT = Path(__file__).with_name("summarize-test-results.py")
SPEC = importlib.util.spec_from_file_location("summarize_test_results", SUMMARY_SCRIPT)
SUMMARY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SUMMARY)
REQUIRED_COVERAGE_REPORTS = SUMMARY.REQUIRED_COVERAGE_REPORTS
main = SUMMARY.main


class SummarizeTestResultsTest(unittest.TestCase):
    def setUp(self):
        self.summary_environment = patch.dict("os.environ", {"GITHUB_STEP_SUMMARY": ""})
        self.summary_environment.start()
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        for report in REQUIRED_COVERAGE_REPORTS:
            report_path = self.root / report
            report_path.parent.mkdir(parents=True, exist_ok=True)
            report_path.write_text(
                '<report><counter type="LINE" missed="20" covered="80"/></report>',
                encoding="utf-8",
            )

        suites = self.root / "mockjdbc/mockjdbc-proxy/target/surefire-reports"
        suites.mkdir(parents=True, exist_ok=True)
        docker_tests = "".join(
            f'<testsuite name="{name}" tests="1" failures="0" errors="0" skipped="0"/>'
            for name in (
                "io.github.rroyoo.mockjdbc.proxy.KafkaMockedQueryEventProducerIntegrationTest",
                "io.github.rroyoo.mockjdbc.wiremock.KafkaMappingConsumerIntegrationTest",
            )
        )
        (suites / "TEST-ci-tests.xml").write_text(
            f"<testsuites>{docker_tests}</testsuites>",
            encoding="utf-8",
        )

    def tearDown(self):
        self.temporary_directory.cleanup()
        self.summary_environment.stop()

    def test_passes_when_all_required_reports_and_tests_are_present(self):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            result = main(self.root)

        self.assertEqual(0, result)
        self.assertIn("Required JaCoCo reports | 4/4", output.getvalue())

    def test_fails_when_a_report_is_missing_even_if_remaining_coverage_is_high(self):
        (self.root / REQUIRED_COVERAGE_REPORTS[-1]).unlink()
        output = io.StringIO()
        errors = io.StringIO()
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
            result = main(self.root)

        self.assertEqual(1, result)
        self.assertIn("Required JaCoCo reports | 3/4", output.getvalue())
        self.assertIn("missing", output.getvalue())
        self.assertIn("reports are missing or invalid", errors.getvalue())


if __name__ == "__main__":
    unittest.main()
