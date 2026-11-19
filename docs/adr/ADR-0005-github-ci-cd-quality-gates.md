# ADR-0005: GitHub CI/CD pipeline and quality gates

- Status: Accepted
- Date: 2026-11-04
- Owners: `mockjdbc-project-steward`

## Context

The repository is public and already uses GitHub Actions to build the Maven reactor and test the standalone Spring sample. The existing workflow did not enforce a code-coverage baseline, publish a concise test summary, or run GitHub-native static analysis. CI must cover both Maven builds, including Docker-backed Testcontainers tests, while keeping the recurring service cost at zero.

The repository owner selected an initial 50% line-coverage gate and a 90% target. The threshold is based on the combined JaCoCo line counters from the reactor modules and the standalone sample; it is not a per-module minimum.

The existing Testcontainers 1.20.4 tests silently skipped on Docker Engine 29 because the old Docker Java client negotiated an API below Docker 29's minimum. The repository uses Testcontainers 1.21.4 with `docker-java.properties` pinning API 1.44 for deterministic compatibility, while retaining the existing Compose API used by the standalone sample.

## Decision

Use GitHub Actions on GitHub-hosted standard `ubuntu-24.04` runners as the only CI orchestration platform. Keep CI checks on pull requests and pushes, support manual runs, and do not publish release artifacts or deploy applications automatically. Release/CD automation is deferred until a versioning and publication policy is adopted.

The workflow must:

1. Build, run tests, and install the `mockjdbc` reactor with Maven so the independent sample can resolve reactor dependencies.
2. Run the `mockjdbc-spring-users` verification suite with Docker available for Testcontainers.
3. Generate JaCoCo XML reports for `mockjdbc-mock`, `mockjdbc-proxy`, `mockjdbc-wiremock`, and `mockjdbc-spring-users`; fail if any report or its line counter is missing, or if combined line coverage is below 50%. Track 90% as the project target; do not make that target a gate until explicitly adopted.
4. Run GitHub CodeQL for Java/Kotlin and publish findings to GitHub code scanning. Skip upload analysis for pull requests from forks, where the token cannot publish security events; build and tests still run.
5. Require both Kafka Testcontainers integration suites to be discovered and pass without skips.
6. Publish test counts, failures, skipped tests, line coverage, and the threshold in the GitHub Actions job summary. Keep detailed logs and CodeQL findings in GitHub; do not upload test/coverage artifacts.

Use minimal workflow permissions, cancel superseded runs for the same branch/PR, and retain the standard runner and Maven cache. Any change to dependencies, scopes, coverage exclusions, or thresholds requires an ADR/checklist update.

## Cost and security constraints

- The repository is public; standard GitHub-hosted runner usage for public repositories is free under GitHub's published Actions policy. Do not select larger runners or paid external scanners.
- GitHub CodeQL code scanning is used for this public repository; no external SaaS token or paid Sonar service is required.
- Results are surfaced as workflow checks, the run summary, and GitHub code-scanning alerts. Avoid artifact retention/storage costs by not uploading artifacts.
- Free-service policies and quotas can change; re-check GitHub billing documentation before changing repository visibility or runner type.

## Quality gates

| Gate | Required result | Published in |
| --- | --- | --- |
| Maven reactor | Build and tests pass | Pull request / Actions check |
| Standalone sample | Build and tests pass; Docker-backed tests run | Pull request / Actions check |
| JaCoCo line coverage | At least 50% combined coverage; 90% is a target | Actions job summary |
| Kafka integration tests | Both Kafka Testcontainers tests pass without skips | Actions job summary |
| CodeQL | Analysis runs for same-repository code and reports findings | GitHub Security / code scanning |

The JaCoCo gate does not imply a guarantee of defect-free code. It establishes a measurable minimum alongside tests, static analysis, and required pull-request checks.

## Rollout checklist

- [x] Run reactor and sample verification on pull requests and pushes.
- [x] Update Testcontainers and pin its Docker API version to support Docker Engine 29.
- [x] Generate JaCoCo reports for both Maven builds.
- [x] Enforce a 50% combined line-coverage minimum and report the 90% target.
- [x] Require complete coverage reports from all four code-bearing projects before calculating the combined gate.
- [x] Publish test and coverage totals in the GitHub Actions run summary.
- [x] Publish CodeQL analysis findings in GitHub code scanning.
- [x] Fail the summary gate if required Kafka integration tests are missing or skipped.
- [x] Configure `main` branch protection to require a passing, up-to-date `verify` check; no review-count gate is enabled.
- [ ] Raise the coverage gate toward 90% as coverage debt is closed.
- [ ] Define version/tag and package-release policy before adding automatic release/deployment.

## Consequences

### Positive

- Pull requests receive a repeatable, visible build/test gate and an explicit coverage baseline.
- Static-analysis findings are available in the repository's existing GitHub security interface.
- The pipeline uses standard runners and GitHub-native reporting without external paid services or retained artifacts.

### Trade-offs

- CodeQL increases workflow duration and may surface existing findings that must be triaged.
- Test/coverage data is summarized in the Actions run; raw XML is available in runner logs/workspace only and is not retained as a downloadable artifact.
- The workflow file cannot configure repository rules. The `main` branch protection setting is managed in GitHub and must be updated if the required check name changes.
- A 50% aggregate baseline can hide weakly tested individual modules. Per-module thresholds remain deferred until a baseline is collected.

## References

- `docs/adr/ADR-0001-project-stewardship-and-gap-closure.md`
- `docs/test-validation-roadmap.md`
- [GitHub Actions billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions)
- [CodeQL Action](https://github.com/github/codeql-action)
- [Testcontainers Docker 29 compatibility issue](https://github.com/testcontainers/testcontainers-java/issues/11211)
