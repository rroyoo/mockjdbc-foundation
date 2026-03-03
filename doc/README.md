# Functional Documentation Index

**Functional specification and user-facing behavior for MockJDBC.**

**For technical architecture and build information**, see [`mockjdbc/README.md`](../mockjdbc/README.md).

---

## Quick Navigation

| Document | Purpose | Audience |
|---|---|---|
| **[Functional Overview](functional-overview.md)** | Goal, capabilities, and main actors | Product managers, QA |
| **[Use Cases (UC-01..05)](use-cases.md)** | 5 key user scenarios with flows | Developers, testers |
| **[Query Mocking Flow](flows/query-mocking-flow.md)** | Sequence diagram and request mapping | Developers, architects |
| **[Main README](../README.md)** | **Primary hub** — start here | Everyone |

---

## Documentation Scope

### ✅ What's Covered Here
- **User intent** — What MockJDBC does for you
- **Use cases** — Real-world scenarios (UC-01..05)
- **Happy path** — Normal execution flow with sequence diagrams
- **Error cases** — Expected failures and recovery
- **Configuration** — How to set up and use
- **Functional inputs/outputs** — Data contracts

### ❌ Not Covered Here
- **Build and architecture** — See [`mockjdbc/README.md`](../mockjdbc/README.md)
- **Module details** — See [`mockjdbc/README.md`](../mockjdbc/README.md)
- **Code implementation** — See Javadoc and skill docs in `.github/skills/`

---

## Document Descriptions

### [Functional Overview](functional-overview.md)
**High-level introduction to MockJDBC as a JDBC mock framework.**

Covers:
- Goal: JDBC-compatible mock execution with gRPC lookup
- Main capabilities: connection management, statement resolution, event publishing
- Functional actors: Application, MockDriver, MockConnection, Adapter, MockQueryService
- Input/output contracts
- Happy path summary (5 steps)
- Common error cases

**Read this first** to understand what MockJDBC does.

### [Use Cases (UC-01..05)](use-cases.md)
**5 concrete user scenarios with preconditions, flows, and results.**

Scenarios:
- **UC-01:** Connect to mock JDBC
- **UC-02:** Resolve a plain SQL mock
- **UC-03:** Resolve a prepared statement mock
- **UC-04:** Resolve a callable statement mock
- **UC-05:** Emit query execution events

**Use this** to see exactly how developers interact with MockJDBC.

### [Query Mocking Flow](flows/query-mocking-flow.md)
**Detailed sequence diagram and low-level request/response mapping.**

Covers:
- Sequence diagram: Application → Driver → Connection → Adapter → gRPC Service
- Request mapping rules by statement type
- Property dependencies (host, port, timeouts)
- Failure points and error handling
- Channel lifecycle notes

**Reference this** when implementing or debugging query resolution.

---

## Style & Conventions

All functional docs follow these principles:

- **Clear and concise** — Avoid jargon; define terms on first use
- **Tables for grouped data** — Properties, inputs, outputs, scenarios
- **Diagrams for flows** — Sequence, flowchart, or state diagrams
- **Examples from use cases** — Real scenarios over abstract descriptions
- **Links and references** — Connect related documents

---

## Relationship to Other Documentation

```
README.md (functional hub)
├── doc/README.md (this file)
├── functional-overview.md
├── use-cases.md
└── flows/query-mocking-flow.md

mockjdbc/README.md (technical)
├── Build, modules, architecture
├── Properties reference
└── Maven structure
```

**Navigation:**
- Start at [main README](../README.md) for overview
- Drill down to functional docs here for details
- Refer to [`mockjdbc/README.md`](../mockjdbc/README.md) for build/architecture

---

## Contributing to Functional Docs

When adding or updating functional documentation:

1. **Use `@DisplayName`-style headers** — Make section purpose clear
2. **Add tables for properties/inputs/outputs** — Group related data
3. **Include flow examples** — Use real UC scenarios
4. **Link between docs** — Reference related sections
5. **Keep it concise** — Avoid dense paragraphs
6. **Update this index** — Reflect new documents here

