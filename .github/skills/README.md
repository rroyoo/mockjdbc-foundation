# Skills Architecture

This directory contains reusable **skills** that enhance agents with specialized capabilities. Skills are modular, composable, and follow the **Single Responsibility Principle**.

## Philosophy

Rather than embedding all expertise in a single agent, we **extract concerns into focused skills**. This allows:

- ✅ **Reusability:** A skill can be used by multiple agents
- ✅ **Maintainability:** Changes to a skill only require updating the skill, not all agents
- ✅ **Composability:** Agents can activate and combine skills as needed
- ✅ **Clarity:** Each skill has a clear, narrow scope

## Current Skills

### Core Domain Skills

#### `tdd-expert` — Test-Driven Development
**Focus:** Writing tests first, TDD workflow, JUnit5, Mockito proficiency  
**Use When:** Creating unit tests, implementing features with TDD, testing legacy code  
**Activated By:** martin-fowler, qa-reviewer (future), software-engineer (when test creation needed)

#### `build-quality` — Build Quality Assurance
**Focus:** Pre-commit validation, compilation, testing, coverage analysis  
**Use When:** Committing code, validating build state, checking test results  
**Activated By:** martin-fowler, software-engineer (before commits)

#### `java-modernizer` — Modern Java Development
**Focus:** Java version-adaptive syntax, records, pattern matching, streams  
**Use When:** Writing/refactoring Java code, modernizing legacy code  
**Activated By:** martin-fowler, software-engineer, any Java-focused agent

#### `commit-expert` — Technical Commit Crafting
**Focus:** Intent-driven commit messages, Conventional Commits, architectural intent  
**Use When:** Creating commit messages, summarizing changes  
**Activated By:** martin-fowler, software-engineer (after code changes)

#### `skill-factory` — Skill Creation & Maintenance
**Focus:** Designing and generating new skills for the repository  
**Use When:** Creating new skills, refining existing skills  
**Activated By:** agent-factory (meta-agent for skill generation)

## How Skills Are Used

### Agent With Skills Example: martin-fowler

```yaml
# .github/agents/martin-fowler.agent.md
skills:
  - commit-expert
  - java-modernizer
  - tdd-expert
  - build-quality
```

**Workflow:**
1. User: "Refactor this OrderService class"
2. Martin Fowler:
   - Analyzes code for smells
   - **Activates `tdd-expert`** → Writes characterization tests
   - **Activates `java-modernizer`** → Refactors with modern Java
   - **Activates `build-quality`** → Runs `mvn clean verify`
   - **Activates `commit-expert`** → Creates architectural commit message

---

## Creating New Skills

Use the **skill-factory** pattern:

### Step 1: Identify the Need
- What domain/responsibility needs its own skill?
- Can this be used by multiple agents?
- Is this distinct from existing skills?

### Step 2: Use the Skill Template
Create `.github/skills/{skill-name}/SKILL.md` following the structure in `skill-factory/SKILL.md`:

```markdown
# Skill: {Name}

## Context
When/why to use this skill

## When to Use This Skill
- Trigger 1: {condition}
- Trigger 2: {condition}

## Core Principles & Guidelines
1. {Principle}: {Guidance}
2. {Principle}: {Guidance}

## Step-by-Step Workflow
1. {Step}: {Action} → {Outcome}
2. {Step}: {Action} → {Outcome}

## Anti-Patterns to Avoid
- ❌ {Bad Practice}: {Why}

## Quality Bar & Verification
- [ ] {Requirement}
- [ ] {Requirement}

## Example Application
{Real-world example}
```

### Step 3: Validate
- [ ] Scope is clear and narrow
- [ ] No overlap with existing skills
- [ ] Examples are concrete
- [ ] Anti-patterns clarify boundaries
- [ ] Quality criteria are measurable

### Step 4: Reference from Agents
Update agent `skills:` list to include the new skill

---

## Best Practices for Skills

### 1. Single Responsibility
Each skill should focus on **ONE domain or capability**.

**Good:** `tdd-expert` (Test-Driven Development)  
**Bad:** `quality-excellence` (too broad)

### 2. Actionable Guidance
Every section should be testable and executable.

**Good:** "Use `@ParameterizedTest` with `@ValueSource` for multiple scenarios"  
**Bad:** "Write good tests"

### 3. Concrete Examples
Include real, copy-paste-ready code samples.

```java
// ✅ GOOD: Concrete example
@Test
@DisplayName("should reject null input")
void testNullInput() {
    assertThrows(IllegalArgumentException.class, 
        () -> service.process(null));
}

// ❌ BAD: Too vague
// Write a test for edge cases
```

### 4. Clear Triggers
Help agents know when to activate the skill.

```markdown
## When to Use This Skill
- Trigger 1: User asks to "write tests" or "create unit tests"
- Trigger 2: Implementing a new feature
- Trigger 3: Refactoring legacy code
```

### 5. Anti-Patterns
Explicitly state what NOT to do.

```markdown
## Anti-Patterns to Avoid
- ❌ **Committing Without Tests:** "I'll test it later"
- ❌ **Ignoring Build Warnings:** Warnings often hide bugs
```

---

## Skill Directory Structure

```
.github/skills/
├── tdd-expert/
│   ├── SKILL.md              ← Skill content (required)
│   └── README.md             ← Optional extended docs
├── build-quality/
│   ├── SKILL.md
│   └── README.md
├── java-modernizer/
│   ├── SKILL.md
│   └── README.md
├── commit-expert/
│   ├── SKILL.md
│   └── README.md
├── skill-factory/
│   ├── SKILL.md              ← How to create skills
│   └── README.md
└── {new-skill}/
    ├── SKILL.md              ← Your new skill
    └── README.md             ← Optional
```

---

## Naming Conventions

**Skill Names:** Use `kebab-case` (lowercase, hyphens)
- ✅ `tdd-expert`, `build-quality`, `api-resilience`
- ❌ `TDDExpert`, `buildQuality`, `APIResilience`

**Skill Domains:** Group by category if many skills exist
- Testing domain: `tdd-expert`, `test-fixtures`, `contract-testing`
- DevOps domain: `build-quality`, `deployment-strategy`, `ci-cd-expert`

---

## Integration with Agents

### Agents Can Reference Multiple Skills

```yaml
# Example: A qa-reviewer agent might use:
skills:
  - tdd-expert          # For test validation
  - build-quality       # For build verification
  - code-review         # For review guidelines (future)
```

### Skills Are Activated Contextually

Agents don't need to use all their skills at once. They activate relevant skills based on the user's request.

```
User: "Write a test for this function"
↓
Agent (martin-fowler) recognizes this is testing work
↓
Activates `tdd-expert` skill
↓
Follows tdd-expert guidance (Arrange-Act-Assert, JUnit5, Mockito, etc.)
```

---

## Future Skills (Candidate List)

- `api-resilience` — Design resilient REST APIs (timeouts, retries, circuit breakers)
- `test-fixtures` — Advanced test fixtures, factories, builders
- `contract-testing` — Test contracts between services
- `proto-design` — Protocol Buffer message design
- `code-review` — Code review checklist and principles
- `documentation-writer` — Technical writing and documentation
- `performance-optimization` — Profiling and optimization techniques
- `security-hardening` — Security-focused code review and patterns
- `release-manager` — Versioning, changelogs, deployment workflows

---

## Tips for Skill Success

1. **Keep Skills Focused:** One skill = one clear domain. If you're tempted to cover multiple topics, split into multiple skills.
2. **Provide Examples:** Real code examples help agents and humans understand exactly what you mean.
3. **Make Triggers Clear:** Agents need to know when your skill is relevant.
4. **Version If Needed:** If a skill changes significantly, document version info (e.g., "Updated for JUnit5 in v2.0").
5. **Iterate:** Skills improve with feedback. Update them as new patterns emerge.


