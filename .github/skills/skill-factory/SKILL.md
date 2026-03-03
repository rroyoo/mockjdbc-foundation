# Skill: Skill Factory - Creating Reusable Skills

## Context
Use this skill when the **agent-factory** (or any agent) needs to **design and generate new skills** for other agents to use. Skills are contextual knowledge bases that guide agents on how to handle specific domains, patterns, or responsibilities.

A skill is:
- A focused, reusable knowledge module in `.github/skills/{skill-name}/SKILL.md`
- A guide that teaches agents **when, how, and why** to apply specific techniques
- Versioned, maintainable, and easy to compose with other skills

## Pre-Analysis: Understanding Skill Requirements

Before creating a skill, answer these questions:

1. **Domain & Audience**
   - What domain does this skill serve? (e.g., Java, Testing, DevOps, Documentation)
   - Which agents or users will benefit from this skill?
   - Is this skill standalone or composable with existing skills?

2. **Scope & Non-Goals**
   - What does this skill **include**? (specific techniques, patterns, tools)
   - What is explicitly **out of scope**? (avoid ambiguity)
   - Does this skill conflict with any existing skills?

3. **Trigger Conditions**
   - When should an agent **activate this skill**?
   - What user queries or code patterns indicate this skill is needed?
   - Are there prerequisite skills or context required?

4. **Knowledge & Best Practices**
   - What are the key principles or rules?
   - What are common pitfalls or anti-patterns?
   - What metrics or verification methods ensure quality?

---

## Core Skill Template (Use This Structure)

```markdown
# Skill: {Skill Name}

## Context
{1-2 sentences describing when/why to use this skill}

## When to Use This Skill
- Trigger 1: {User query or code pattern}
- Trigger 2: {User query or code pattern}
- Prerequisite: {Required context or skills}

## Core Principles & Guidelines

### 1. {Principle Name}
{Clear, actionable guidance}

### 2. {Principle Name}
{Clear, actionable guidance}

### 3. {Principle Name}
{Clear, actionable guidance}

## Step-by-Step Workflow

1. **[Step Name]:** {Action} → {Outcome}
2. **[Step Name]:** {Action} → {Outcome}
3. **[Step Name]:** {Action} → {Outcome}

## Anti-Patterns to Avoid
- ❌ {Bad Practice}: {Why it fails}
- ❌ {Bad Practice}: {Why it fails}

## Quality Bar & Verification
A skill is complete when:
- [ ] {Requirement}
- [ ] {Requirement}
- [ ] {Requirement}

## Example Application
{Concrete, real-world example showing the skill in action}
```

---

## Guidelines for Skill Design

### Clarity Over Comprehensiveness
- Keep skills **focused and concise** (typically 30-60 lines).
- Avoid encyclopedic depth; instead, link to external resources if needed.
- Use **active voice** and **imperative mood** (e.g., "Apply", "Validate", "Ensure").

### Actionability
- Every guideline should be **testable and executable**.
- Provide **concrete examples** rather than abstract theory.
- Include **anti-patterns** to clarify boundaries.

### Modularity & Composition
- Design skills so they can be **combined** with other skills.
- Clearly state **prerequisite skills** or context.
- Avoid duplicating guidance that already exists in other skills.

### Discoverability & Documentation
- Use **consistent naming** (e.g., `{domain}-{responsibility}` like `java-testing`, `git-workflow`).
- Place skills in `.github/skills/{skill-name}/SKILL.md`.
- Include a `Context` section that helps agents recognize when to activate the skill.

---

## Skill Lifecycle

| Phase | Action | Responsible |
|-------|--------|-------------|
| **Design** | Review scope, principles, triggers with stakeholders | Skill Creator |
| **Implementation** | Write SKILL.md following the template | Skill Creator |
| **Testing** | Apply skill in 2+ real scenarios; verify completeness | Agent/User |
| **Review** | Check for clarity, anti-patterns, conflicts with other skills | Team Lead |
| **Documentation** | Add skill to `.github/skills/` directory and index | Maintainer |
| **Maintenance** | Update skill if new patterns emerge or context changes | Skill Maintainer |

---

## Common Skill Categories

### Domain Skills
- `java-modernizer` — Write modern, idiomatic Java (versions 11, 17, 21+)
- `testing-pyramid` — Design and implement testable, maintainable test suites
- `proto-contract` — Design Protocol Buffer messages and gRPC contracts

### Process Skills
- `commit-expert` — Write intent-driven, clean commit messages
- `code-review` — Review code for correctness, clarity, and maintainability
- `release-manager` — Manage versioning, changelogs, and deployment workflows

### Architecture Skills
- `microservices-design` — Design loosely-coupled, independently deployable services
- `error-handling` — Implement consistent, recoverable error strategies
- `observability-first` — Design systems with logging, metrics, and tracing

---

## Hard Rule: Skill-First Extraction
- If a request adds **technical capability** (testing, build checks, security, observability, API design), create or update a skill first.
- Update an agent directly only for **persona-level concerns** (role, tone, authority boundaries, escalation behavior).
- If both apply, deliver **two artifacts**: concise agent update + dedicated skill.

## Decision Matrix (Mandatory)
- **Capability reusable by multiple agents?** -> Create skill.
- **Needs long procedural guidance/examples?** -> Create skill.
- **Changes who the agent is (not just what it can do)?** -> Update agent.
- **Unsure?** -> Choose skill and keep agent minimal.

## Validation Checklist for New Skills

When creating a skill, ensure:

- [ ] **Skill-first rule applied:** Capability was not inlined into the agent unnecessarily.
- [ ] **Scope is clear:** Domain, audience, and non-goals are explicit.
- [ ] **Triggers are specific:** Users know when to activate this skill.
- [ ] **Principles are actionable:** Each guideline can be tested or verified.
- [ ] **No duplication:** The skill doesn't conflict with existing skills.
- [ ] **Examples are concrete:** Real-world scenarios demonstrate the skill.
- [ ] **Anti-patterns are clear:** Users understand what **not** to do.
- [ ] **Quality bar is measurable:** "Done" has clear criteria.
- [ ] **Naming is discoverable:** The skill name matches its purpose and domain.

---

## Anti-Pattern: Bloated Agent
- ❌ Putting long testing/build/security playbooks directly in `.agent.md` files.
- ✅ Keep `.agent.md` concise and delegate technical depth to `.github/skills/{skill-name}/SKILL.md`.

---

## Example: Creating a New Skill

**User Request:** "Create a skill for designing resilient APIs."

**Skill Factory Analysis:**
1. **Domain:** API Design (REST, gRPC, async)
2. **Audience:** Backend engineers, architects
3. **Triggers:** "Design an API", "Make this endpoint more resilient", "Handle failures gracefully"
4. **Principles:** Timeouts, retries, circuit breakers, idempotency, observability
5. **Conflicts:** None (complements `error-handling` skill)

**Output Skill:** `.github/skills/api-resilience/SKILL.md`

---

## Tips for Skill Factory Agents

When automating skill creation:

1. **Validate Scope:** Ask the user or infer from context whether the skill is too broad or too narrow.
2. **Check for Conflicts:** Search existing skills for overlapping domains.
3. **Test with Examples:** Apply the skill to 1-2 real scenarios before finalizing.
4. **Iterate:** Skills improve with feedback; version them if needed.
5. **Document Triggers:** Help agents recognize when to use the skill.
