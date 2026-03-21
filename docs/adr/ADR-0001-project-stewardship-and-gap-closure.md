# ADR-0001: Project stewardship model and prioritized gap-closure backlog

- Status: Accepted
- Date: 2026-03-21

## Related ADRs

- `docs/adr/ADR-0002-jdbc-proxy-capture-and-kafka-event-pipeline.md`
- `docs/adr/ADR-0003-mock-driver-scope-and-jdbc-compatibility.md`

## Context

The repository already has meaningful implementation progress, especially in `mockjdbc-mock` and in the standalone sample project `mockjdbc-spring-users`, but the project does not yet have a repository-level custom agent that owns:
- gap inventory
- module status decisions
- roadmap/checklist maintenance
- documentation alignment
- quality-gate follow-through

The existing custom agent, `jdbc-interface-implementer`, is still useful, but it is a specialist. It focuses on ByteBuddy-based JDBC interface implementation work and should not be responsible for repo-wide governance.

During the repository review, the following verified gaps were detected:

### Governance and documentation
- There was no repository steward agent.
- There was no ADR documenting current gaps and completion strategy.
- No GitHub Actions workflows were found under `.github/workflows/`.
- `mockjdbc/README.md` describes several classes and flows that do not match the current implementation style based on factories, handlers, and ByteBuddy wiring.

### Module status and scope
- `mockjdbc-mock` is active and has substantial implementation and test coverage.
- `mockjdbc-proto` is active as the contract module.
- `mockjdbc-proxy` is declared and documented, but no production source tree was found under `mockjdbc/mockjdbc-proxy/src/main/java`.
- `mockjdbc-spring-users` works as an independent sample project, but still relies on local jars produced by the sibling build.

### Module decision record
- `mockjdbc-proxy` target status is **experimental placeholder retained in the reactor**.
- Rationale: preserve module identity and dependency intent while avoiding false claims of completed functionality.
- Constraint: until production code is added, docs must keep its status explicit as placeholder/experimental.
- Exit criteria to move from placeholder to active:
  - add production source under `mockjdbc/mockjdbc-proxy/src/main/java`
  - add tests for its public behavior
  - update architecture docs to reference concrete classes

### Proxy product direction (new scope)
- Proxy product scope is now governed in `docs/adr/ADR-0002-jdbc-proxy-capture-and-kafka-event-pipeline.md`.
- This ADR (`ADR-0001`) keeps proxy items only as governance backlog entries.

### Build and integration hygiene
- `mockjdbc-spring-users/pom.xml` currently uses `systemPath` dependencies pointing to sibling `target/` jars.
- That wiring works locally after building the sibling project, but it is fragile for repeatable CI and onboarding.

### JDBC implementation coverage
- `ConnectionFactory` covers important stateful areas, but `Connection` is still partial. Missing or not yet wired areas include `nativeSQL`, metadata/type-map methods, LOB factory methods, `isValid`, `abort`, wrapper methods, and modern request/sharding methods.
- `StatementFactory` is much more complete, but it still does not fully cover the entire JDBC contract. Notable gaps include wrapper methods and some newer convenience methods.
- Some statement overloads accept `resultSetType`, `resultSetConcurrency`, and `resultSetHoldability`, but current implementation does not clearly enforce those values end-to-end.

### Result-set fidelity
- `ResultSetFactory` already materializes gRPC responses into a cached result set, but it currently supports a limited subset of JDBC value kinds and metadata fidelity.

### Test posture
- `mockjdbc` tests pass locally.
- `mockjdbc-spring-users` tests pass locally.
- Test coverage is concentrated mainly in `mockjdbc-mock`; broader packaging and CI automation gaps remain.

## Decision

The repository adopts a two-level custom-agent model:

1. **`mockjdbc-project-steward` is the repository owner agent**
   - Owns repo-wide gap inventory, module decisions, checklist maintenance, ADR alignment, and quality-gate follow-through.
   - Reads this ADR first and uses it as the canonical backlog.

2. **`jdbc-interface-implementer` remains as a specialist agent**
   - Focuses on ByteBuddy-based JDBC interface implementation work.
   - Should be delegated tasks that are primarily about `ConnectionFactory`, `StatementFactory`, delegated handlers, and related tests.

3. **Steward skills are added for repository governance**
   - `project-gap-audit`
   - `module-status-governance`
   - `dependency-integration-hygiene`
   - `documentation-consistency`
   - `roadmap-checklist-governance`
   - `quality-gates-triage`

4. **Existing JDBC-focused skills are retained**
   - They still make sense for low-level implementation work.
   - They are not sufficient on their own for repo stewardship.

5. **`mockjdbc-proxy` evolves from placeholder to productized capture pipeline**
   - Product requirements, event contract, and rollout slices are governed in `ADR-0002`.

6. **Mock driver compatibility scope is governed explicitly**
   - Driver behavior boundaries and compatibility roadmap are governed in `ADR-0003`.

## Assessment of existing agents and skills

### Agents
- `jdbc-interface-implementer`: **keep**. It still makes sense as a specialist agent.
- Missing repo owner agent: **addressed by adding `mockjdbc-project-steward`**.

### Skills
These existing skills still make sense and should be kept for specialist JDBC work:
- `bytebuddy-matcher-safety`
- `connection-factory-tests`
- `connection-method-inventory`
- `connection-state-semantics`
- `delegation-handler-catalog`
- `sql-exception-policy`
- `model-selection-policy`

Assessment notes:
- They are useful and coherent for `ConnectionFactory` and related ByteBuddy delegation work.
- They are too narrow to govern the full repository lifecycle.
- `connection-method-inventory` is still useful today, even if a future generic `jdbc-interface-coverage` skill may eventually replace it.
- `model-selection-policy` is generic rather than domain-specific, but still harmless and reusable.

## Steward operating checklist

### A. Governance baseline
- [x] Add a repository steward agent.
- [x] Add repository-governance skills for the steward agent.
- [x] Add an ADR with verified gaps and a prioritized checklist.
- [x] Create dedicated ADR for proxy product scope (`ADR-0002`).
- [x] Create dedicated ADR for mock driver scope (`ADR-0003`).
- [ ] Add GitHub Actions workflows for build and test automation.

### B. Documentation alignment
- [x] Update `mockjdbc/README.md` so its architecture section reflects the current factory/handler/ByteBuddy implementation.
- [x] Document the current status of `mockjdbc-proxy` explicitly instead of implying full implementation.
- [x] Add or update a short “supported JDBC surface” document for `Connection`, `Statement`, `PreparedStatement`, and `CallableStatement`.

### C. Module decisions
- [x] Confirm the target status of `mockjdbc-proxy`: complete, mark experimental, or remove from the active module path until implemented.
- [x] Maintain a module status matrix in docs or ADR updates as the repo evolves.
- [ ] Reclassify `mockjdbc-proxy` from placeholder to active after minimal capture + Kafka publication slice is verified.

### D. Build and integration hygiene
- [ ] Replace or reduce `systemPath` usage in `mockjdbc-spring-users` with a more reproducible consumption model.
- [x] If `systemPath` remains temporarily, document the bootstrap path clearly and keep it accurate.

### E. JDBC coverage backlog
#### Connection
- [ ] Implement and test `nativeSQL`.
- [ ] Implement and test metadata/type-map behavior (`getMetaData`, `getTypeMap`, `setTypeMap`).
- [ ] Implement and test LOB factory methods (`createBlob`, `createClob`, `createNClob`, `createSQLXML`).
- [ ] Implement and test `createArrayOf` and `createStruct`.
- [ ] Implement and test `isValid` and `abort`.
- [ ] Implement and test `unwrap` and `isWrapperFor`.
- [ ] Implement and test request/sharding methods or explicitly document them as unsupported.

#### Statement family
- [ ] Audit and complete remaining wrapper and modern convenience methods.
- [ ] Decide and enforce semantics for overloads carrying result-set type, concurrency, and holdability.
- [ ] Add targeted tests for any remaining partial or unimplemented methods.

### F. Result-set fidelity
- [ ] Expand `ResultSetFactory` type support beyond the current subset where needed.
- [ ] Add dedicated tests for result-set materialization and metadata fidelity.

### G. Test and release posture
- [ ] Add an integration test that validates `DriverManager` + SPI discovery end-to-end.
- [ ] Keep `mockjdbc` and `mockjdbc-spring-users` tests green after each milestone.
- [ ] Introduce automated verification for both the reactor build and the standalone sample.

### H. Proxy implementation and adoption
- [ ] Follow `ADR-0002` checklist for proxy event contract, capture, Kafka publication, and integration guides.

## Next recommended slice

The steward agent should normally take the next unchecked item from this order:
1. proxy Proto event contract draft (`mockjdbc-proxy` + `mockjdbc-proto`)
2. datasource identity model and multi-datasource capture wiring
3. Kafka publication path for Proto events
4. integration guides for generic Java JDBC adoption
5. sample-app dependency hygiene
6. `Connection` coverage backlog
7. remaining `Statement` contract gaps
8. result-set fidelity expansion
9. CI automation

## Consequences

### Positive
- The repository now has a clear owner agent for project-wide completion work.
- Specialist JDBC implementation work remains delegated to the existing focused agent.
- The backlog is now explicit, reviewable, and incremental.

### Trade-offs
- The steward agent introduces process and documentation overhead.
- The ADR checklist must be maintained actively or it will drift.

## Review rule

Whenever a significant gap is closed or a module decision changes, update this ADR checklist in the same change set whenever practical.
