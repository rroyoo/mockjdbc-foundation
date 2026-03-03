# Skill: Technical Commit Architect

## Context
Use this skill when the user asks to summarize changes or prepare a git commit message. **CRITICAL: Never commit unless the project compiles and all tests pass.**

## Pre-Commit Validation (MANDATORY)

**BEFORE any commit, you MUST validate:**

1. ✅ **Code Compiles**
   ```bash
   mvn clean compile
   # or: gradle build --dry-run
   ```
   - No compilation errors
   - No warnings about unused code (per java-modernizer skill)
   - All generated sources present

2. ✅ **All Tests Pass**
   ```bash
   mvn test
   # or: gradle test
   ```
   - 100% of tests pass (no skipped tests unless explicitly ignored)
   - No flaky tests (deterministic, <100ms per test)
   - All test output is clean (no spurious warnings)

3. ✅ **Code Coverage ≥80% (if applicable)**
   ```bash
   mvn jacoco:report
   # Check target/site/jacoco/index.html
   ```
   - Business logic at least 80% covered
   - Critical paths (security, validation) 100% covered

4. ✅ **No Unused Code**
   - Per `java-modernizer` skill: no unused imports, variables, methods, or dead code
   - No commented-out code (exception: explanatory comments for workarounds)

## Validation Failure Protocol

**If ANY validation fails:**
- ❌ DO NOT commit
- 📋 Report which check failed and why
- 🔧 Fix the issue (recompile, fix test, add test, remove dead code)
- ✅ Re-validate before attempting commit again

**Never force-push or skip validation checks.** A failed build breaks trust with your team.

## Commit Message Structure

Once ALL validations pass:

### 1. **Analyze Diff**
   - Look at staged changes
   - Identify the architectural intent (not just mechanical changes)

### 2. **Fowler Style**
   - Write messages that emphasize **Intent** (why) over **Mechanism** (what lines)
   - Think: "This change improves X because..."

### 3. **Conventional Commits Format**
   ```
   <type>(<scope>): <subject>
   
   <body>
   
   <footer>
   ```

   Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`
   
   Example:
   ```
   refactor(adapter): replace conditional with polymorphism in QueryServiceAdapter
   
   The nested if-else was becoming a 'Long Function' smell. Introduced a Strategy
   pattern to handle different transport mechanisms (gRPC, REST, in-memory), improving
   maintainability and testability.
   
   Fixes: #42
   ```

### 4. **Subject Line Rules**
   - **Imperative mood:** "Refactor...", "Introduce...", "Fix..." (not "Refactored", "Fixed")
   - **Max 50 characters** (GitHub default truncation)
   - **Lowercase** (except proper nouns)
   - **No period at end**

### 5. **Body Guidelines**
   - **Explain the WHY**, not the WHAT (diffs show WHAT)
   - Reference code smells fixed (Long Function, Primitive Obsession, etc.)
   - Mention patterns applied (Strategy, Adapter, Template Method)
   - Link to issues/tickets if applicable

### 6. **Metrics & Observability**
   - If the change includes custom metrics (e.g., `http_request_by(cos=xxx)`), explain the observability benefit
   - Example: "Adds instrumentation for request latency by service tier, enabling SLO tracking"

## Example: Good Commit

```
refactor(maven): centralize version management in parent POM

Before: Child modules had hardcoded dependency versions scattered across 20+ places.
After: Single source of truth in parent <dependencyManagement>.

Benefits:
- Eliminates version conflicts
- Simplifies upgrades
- Aligns with maven-management skill rules (alphabetically sorted properties)

All tests pass. No warnings. Coverage: 92%.
```

## Example: Bad Commits to Avoid

❌ **No validation before commit:**
```bash
git commit -m "Fix stuff"  # Tests not run!
```

❌ **Mechanical description:**
```
refactor: changed getDefault to get
```
(Missing intent: WHY did we rename?)

❌ **Too vague:**
```
refactor: cleanup
```
(Cleanup of WHAT?)

❌ **Committing broken code:**
```
refactor: WIP - still fixing compilation errors
```
(Never commit broken code!)

## Workflow (Enforced)

1. **Make changes** → Edit files
2. **Validate compilation** → `mvn clean compile` (✅ or 🔧 fix)
3. **Run all tests** → `mvn test` (✅ or 🔧 fix)
4. **Check coverage** → `mvn jacoco:report` (✅ or 🔧 add tests)
5. **Review code** → Per java-modernizer: no unused code
6. **Write intent-focused message** → Fowler style, Conventional Commits
7. **Commit** → `git commit -m "..."`
8. **Verify commit** → `git log -1 --stat`

## Tools & Commands

```bash
# Full validation before commit
mvn clean verify jacoco:report

# Quick compile check
mvn clean compile

# Run tests
mvn test

# View coverage report
open target/site/jacoco/index.html

# Check git status before commit
git status
git diff --cached

# Atomic commit (stage + commit)
git add <files>
git commit -m "..."

# Verify what you committed
git log -1 --stat
git show --stat
```

## Enforcement Rules

- **Never commit without validation.** Period.
- **If tests fail, fix them or add tests.** Don't ignore failures.
- **If code doesn't compile, fix it before committing.**
- **If there's unused code, clean it per java-modernizer skill.**
- **If coverage drops, add tests.** No exceptions.

## Related Skills

- `java-modernizer` — Code cleanliness (no unused code)
- `tdd-expert` — Test design and test-driven development
- `build-quality` — Compilation, test execution, coverage validation
- `maven-management` — POM structure and version management

