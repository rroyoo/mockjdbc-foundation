# Open-Source Launch Roadmap

Last updated: 2026-11-28

## Purpose and scope

This roadmap prepares MockJDBC for public adoption: users should be able to understand the project, consume its Maven artifacts, follow a working example, and contribute changes.

MockJDBC is a debugging and testing tool. It is not intended to be deployed in production. This roadmap does not make production deployment readiness or complete JDBC API parity a launch requirement. Documentation must state the supported JDBC surface and known limitations clearly. Because debugging may involve realistic data, document what query parameters and results can be captured and where those events are sent.

The JDBC implementation backlog in [`next-steps.md`](next-steps.md) is separate and remains paused while this launch roadmap is prioritized.

Statuses: `done`, `partial`, `open`, `deferred`, `blocked`.

## Current baseline

### Done

- The Maven reactor contains the reusable `mockjdbc-proto`, `mockjdbc-mock`, `mockjdbc-proxy`, and `mockjdbc-wiremock` modules.
- GitHub Actions builds and tests the reactor and standalone sample, checks Protobuf compatibility, and runs CodeQL.
- `mockjdbc-spring-users` provides a working example, including an end-to-end record/replay test.
- Engineering documentation includes the module status matrix, JDBC support matrix, architecture decisions, and integration validation evidence.
- A pull request template is present.
- Apache License 2.0 is in place (`LICENSE`), and the parent POM carries publication metadata (`name`, `description`, `url`, `licenses`, `developers`, `scm`), inherited by every reactor module, plus per-module `name`/`description`.
- The repository has a root `README.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, and `SECURITY.md`.
- GitHub issue forms exist for bug reports, documentation issues, and feature requests, plus a `config.yml` linking to private security reporting and Discussions.
- The GitHub repository description and topics are set, and Discussions is enabled.

### Partial

- Existing READMEs explain architecture and sample setup, but are not yet a cohesive, beginner-friendly user guide.
- Some README details need reconciliation with the current POMs and build flow before becoming the canonical onboarding instructions.

### Open

- There is no Docusaurus site or documentation deployment workflow.
- Maven Central publication tooling (release profile, publishing plugin, release workflow) is in place, but no namespace credentials/GPG secrets exist yet and no beta tag has been pushed.
- The repository homepage URL is still unset (pending the Docusaurus site's public URL).

## Roadmap

### 1. Define the public project contract (`done`)

- [x] Confirm the project description, audience, name, and supported debugging/testing use cases.
- [x] Choose and add an open-source license before inviting external contributions. **Decision: Apache License 2.0.**
- [x] Decide which modules will be published in the first beta and describe their maturity and limitations. **Decision: `mockjdbc-proto`, `mockjdbc-mock`, `mockjdbc-proxy`, `mockjdbc-wiremock`; `mockjdbc-spring-users` stays a sample, not a published artifact.**
- [x] Define the versioning policy, including whether the first public version is `0.x` or `1.0.0` beta. **Decision: `0.x.0-beta.N`, e.g. `0.1.0-beta.1`.**
- [x] Confirm ownership of the Maven Central namespace for the existing `io.github.rroyoo.mockjdbc` coordinates. **Verified** via Sonatype Central Portal GitHub login (`io.github.rroyoo` namespace is `Verified`). Credentials/tokens will be added only as GitHub Actions secrets in Phase 4, never committed to the repo.

**Exit criteria:** the repository states what MockJDBC is for, what it does not support, which modules are offered, and under what license and versioning policy.

### 2. Make the repository welcoming to users and contributors (`partial`)

- [x] Add a root `README.md` with the problem statement, a record/replay diagram, beta status, module overview, quick install/use example, documentation link, and known limitations.
- [x] Add `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, and a support guide with clear reporting channels.
- [x] Add issue forms or templates for bugs, documentation problems, and feature requests. Keep the existing pull request template and align it with the contributor guide.
- [x] Set the GitHub repository description, topics, website URL, and social preview image. *(Website URL and social preview image remain pending until the Docusaurus site exists — see Phase 3.)*
- [ ] Make the first README example reproducible from the documented Maven coordinates rather than relying on stale local paths or generated JARs.

**Exit criteria:** a first-time visitor can quickly assess the project, run the introductory example, report an issue, and understand how to contribute.

### 3. Build and publish user documentation with Docusaurus (`open`)

- [ ] Create a Docusaurus user-documentation site, preferably under `website/`, separate from the engineering ADRs and repository-maintenance documents in `docs/`.
- [ ] Organize the guide around user tasks:
  - What MockJDBC does and how capture/replay works.
  - Getting started with the mock JDBC driver from Maven coordinates.
  - Capturing JDBC activity with the proxy agent.
  - Replaying queries through the WireMock bridge.
  - A complete record-to-replay tutorial based on `mockjdbc-spring-users`.
  - Configuration reference, supported JDBC surface, known limitations, and troubleshooting.
  - Contributing and release-status information.
- [ ] Use executable examples from the sample project where possible, so documentation does not drift from tested behavior.
- [ ] Make English the initial documentation language; consider translations after the English guides are complete.
- [ ] Add a GitHub Actions check that builds the site on pull requests and deploys it to GitHub Pages after changes land on the default branch. Pull requests must validate the site without publishing it.
- [ ] Configure the Docusaurus `url` and `baseUrl` for the repository's eventual Pages URL and add that URL to the repository metadata and root README.

**Exit criteria:** the site builds in CI, is available from the repository, and a new user can complete the end-to-end tutorial by following it without undocumented local setup.

### 4. Prepare and validate Maven beta artifacts (`partial`)

- [x] Decide the exact artifact set. The likely set is the parent POM plus the reusable reactor modules; keep `mockjdbc-spring-users` as a sample rather than a published library. **Confirmed: `mockjdbc` (parent), `mockjdbc-proto`, `mockjdbc-mock`, `mockjdbc-proxy`, `mockjdbc-wiremock`.**
- [x] Complete inherited Maven metadata: project name and description, URL, license, SCM, developers, and any other Central-required fields.
- [x] Configure source and Javadoc JARs and artifact signing for publishable JAR modules. Done via the `release` Maven profile in `mockjdbc/pom.xml` (`maven-source-plugin`, `maven-javadoc-plugin`, `maven-gpg-plugin`), activated only with `-Prelease`; verified locally (sources/javadoc jars generate correctly, GPG signing fails as expected without a key).
- [x] Configure publication through the Maven Central Portal, keeping credentials in protected GitHub Actions secrets or environments. Done via `central-publishing-maven-plugin` (`autoPublish=false`) and the `.github/workflows/release.yml` workflow, gated behind a `maven-central-release` GitHub Environment.
- [x] Publish only from an intentional version tag, with a manual approval step for the first beta. Do not publish from pull requests or ordinary CI builds. The release workflow only triggers on `v*.*.*` tags (or manual `workflow_dispatch` for a dry run), and `autoPublish=false` means every bundle still needs a manual "Publish" click in the Central Portal.
- [ ] Test the released coordinates from a clean consumer project that does not depend on reactor outputs or pre-installed snapshot artifacts. **Blocked until the namespace/credentials below are set up and a real tag is pushed.**
- [x] Document module-specific runtime requirements, especially the WireMock extension packaging and its provided WireMock/gRPC runtime dependencies. See [`mockjdbc-wiremock/README.md`](../mockjdbc/mockjdbc-wiremock/README.md#packaging-and-runtime-requirements-for-publication).
- [x] Treat every Central beta version as permanent and immutable. Use a new version for each beta (for example, `0.x.0-beta.1`, `0.x.0-beta.2`); use a non-Central repository for disposable builds. The parent POM's CI-friendly `revision` property defaults to `0.1.0-SNAPSHOT` for local/dev builds; the release workflow overrides it with `-Drevision=<tag-derived-version>`.

**Still needed before the first tag can be pushed (maintainer action, requires the verified `io.github.rroyoo` namespace):**

1. In [central.sonatype.com → Account → Generate User Token](https://central.sonatype.com/account), generate a Central Portal token (username/password pair). Store them as repository secrets `CENTRAL_PORTAL_USERNAME` and `CENTRAL_PORTAL_PASSWORD`.
2. Generate a dedicated GPG key for releases (`gpg --full-generate-key`), publish its public key to a keyserver (e.g. `gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>`, required so Central can verify signatures), then export the private key (`gpg --armor --export-secret-keys <KEYID>`) and store it as secret `MAVEN_GPG_PRIVATE_KEY`; store its passphrase as secret `MAVEN_GPG_PASSPHRASE`.
3. Create a GitHub Environment named `maven-central-release` (Settings → Environments) and add the four secrets there (not at the repository level) so publishing requires the environment's protection rules; optionally require a manual reviewer approval on that environment for extra safety.
4. Push an annotated tag such as `v0.1.0-beta.1` to trigger `.github/workflows/release.yml`, then visit <https://central.sonatype.com/publishing/deployments> to review and manually publish the validated bundle.

**Exit criteria:** all intended artifacts resolve from Maven Central in a clean consumer project, their metadata and signatures are valid, and the sample runs using those published coordinates.

### 5. Launch the beta and invite the community (`open`)

- [ ] Publish a beta release with installation instructions, module coordinates, release notes, and known limitations.
- [ ] Link the Docusaurus site and Maven artifacts from the root README and GitHub repository metadata.
- [ ] Enable GitHub Discussions if maintainers can support it, and create a small set of actionable `good first issue` and `help wanted` issues.
- [ ] Announce the release in relevant Java, JDBC, database-testing, and developer-tool communities without promising unsupported behavior.
- [ ] Track onboarding problems, documentation feedback, external issues, forks, and contributions. Treat stars as a useful discovery signal, not the primary measure of user success.

**Exit criteria:** an external user can install the beta, follow the tutorial, understand its limitations, and find a clear route to ask questions or contribute.

### 6. Iterate from beta feedback (`deferred`)

- [ ] Use external usage and issue reports to prioritize documentation fixes and debugging/test-use improvements.
- [ ] Maintain release notes and update the support matrix when behavior changes.
- [ ] Define explicit exit criteria for a stable release based on a reliable user workflow, compatibility expectations, and resolved beta feedback.

**Exit criteria:** maintainers can explain what changes between beta releases and what evidence is required before declaring a stable API. Full JDBC parity and production deployment are not required unless the project scope changes.

## Sequencing and decisions

- License and public scope decisions must precede external contributions and public artifact publication.
- The root README and Docusaurus site can be drafted while Maven publication is prepared; the final install tutorial depends on the chosen artifact coordinates.
- The beta launch depends on a working user guide and a clean-consumer test against published artifacts.
- The existing reactor/sample CI is the foundation. Add documentation and release workflows without replacing the current test, Protobuf compatibility, or CodeQL checks.
- Data masking and bridge retries may be considered based on debugging use cases, but production deployment readiness is not a launch gate. Documentation must still explain the handling of captured SQL, parameters, and results.

## References

- [LangChain4j documentation site README](https://github.com/langchain4j/langchain4j/blob/main/docs/README.md)
- [Docusaurus: deploy to GitHub Pages](https://docusaurus.io/docs/deployment#deploying-to-github-pages)
- [Maven Central publishing requirements](https://central.sonatype.org/publish/requirements/)
- [Maven Central immutability policy](https://central.sonatype.org/publish/requirements/immutability/)
