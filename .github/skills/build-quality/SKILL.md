# Skill: Build Quality Assurance

## Context
Use this skill when you need to validate code quality before committing. This skill is a **GATING SKILL** — code MUST pass ALL checks before proceeding. **NEVER commit without validating through this skill.**

## CRITICAL ENFORCEMENT RULES (Non-Negotiable)

### Rule 1: NEVER Commit Without Validation
- ❌ NO commits without `mvn clean compile`
- ❌ NO commits without `mvn test` passing
- ❌ NO commits if ANY check fails
- ❌ NO commits if terminal is unresponsive
- ❌ NO commits if output is ambiguous

### Rule 2: Explicit Exit Code Verification
```bash
mvn clean compile && echo "COMPILE_OK" || echo "COMPILE_FAILED"
mvn test && echo "TEST_OK" || echo "TEST_FAILED"
echo "Exit code: $?"  # MUST be 0
```
- Do NOT assume success from empty output
- Do NOT trust unclear/ambiguous output
- Capture and verify exit code explicitly

### Rule 3: Terminal Responsiveness Gate
Before running ANY validation:
```bash
echo "GATE_TEST" && date  # Must return immediately
# If no response in 5 seconds: STOP, do NOT commit
```

### Rule 4: Build Success Confirmation
You MUST see these exact strings in output:
- `[INFO] BUILD SUCCESS` ← Mandatory indicator
- `Tests run: X, Failures: 0, Errors: 0` ← For unit tests
- No `[ERROR]` messages ← Absolutely required
- No `[FATAL]` messages ← Absolutely required

## When to Use This Skill
- **Trigger 1:** User says "I'm ready to commit" or "commit my changes"
- **Trigger 2:** Code has been modified and needs validation
- **Trigger 3:** You need to verify the project state before integration
- **Trigger 4:** User mentions "build", "test", "verify", "commit"
- **Prerequisite:** Maven or Gradle build tool configured in project

## Core Principles & Guidelines

### 1. Pre-Commit Validation Checklist (Sequential Gates)
Execute this sequence. **STOP if ANY step fails.**

```bash
# Gate 1: Terminal Responsiveness
echo "VALIDATION_GATE" && date
# If no response: STOP, report terminal issue

# Gate 2: Compilation
mvn clean compile
# If fails: STOP, report compilation error

# Gate 3: Unit Tests
mvn test
# If fails: STOP, report test failure

# Gate 4: Integration Tests  
mvn verify
# If fails: STOP, report integration failure

# Gate 5: Code Coverage (if applicable)
mvn jacoco:report
# If < 80%: STOP, add tests
```

### 2. Build State Requirements
- ✅ **Green Build:** All tests pass, no compilation errors, `[INFO] BUILD SUCCESS`
- ✅ **Code Coverage:** ≥80% for business logic, 100% for critical paths
- ✅ **No Warnings:** Resolve compiler warnings or document exceptions
- ✅ **Deterministic:** Build passes consistently, no flaky tests (each test <100ms)
- ✅ **No Dead Code:** No unused imports, variables, or commented code (per java-modernizer)

### 3. Fail-Fast Approach
- Stop immediately if compilation fails
- Do NOT proceed if tests fail
- Do NOT proceed if terminal is unresponsive
- Report first failure clearly (not all failures at once if possible)
- Provide actionable error messages

### 4. Module-Level Validation (for multi-module projects)
- Validate only affected modules when possible: `mvn -pl {module-name} -am clean verify`
- Full build validation before final commit: `mvn clean verify`

## Step-by-Step Validation Workflow

1. **Terminal Gate:** Verify responsiveness with `echo "TEST" && date`
2. **Compile Check:** Run `mvn clean compile`. Fix compilation errors immediately. **STOP if fails.**
3. **Unit Test Execution:** Run `mvn test`. All tests must pass. **STOP if fails.**
4. **Integration Test Execution:** Run `mvn verify`. All integration tests must pass. **STOP if fails.**
5. **Coverage Analysis:** Run `mvn jacoco:report`. Review coverage report. **STOP if < 80%.**
6. **Code Cleanliness:** Verify no unused code (per java-modernizer skill). **STOP if issues found.**
7. **Green Build Confirmation:** Confirm ALL steps passed with clear indicators.
8. **Commit Only After Step 7:** Only then proceed to `commit-expert` skill.

## Common Build Failure Scenarios

### Scenario A: Compilation Error
```
[ERROR] COMPILATION ERROR:
[ERROR] /path/to/File.java:[line]: error description
```
**Action:** 
- ❌ STOP immediately
- Report the exact compilation error
- Do NOT proceed to testing
- Do NOT commit
- User must fix source code and re-validate

### Scenario B: Test Failure
```
[ERROR] FAILURE: ...
[INFO] Tests run: 5, Failures: 1, Errors: 0
[INFO] BUILD FAILURE
```
**Action:**
- ❌ STOP immediately
- Report which test failed and why
- Do NOT commit
- User must fix test or implementation and re-validate

### Scenario C: Terminal Unresponsive
```
(no output for > 5 seconds)
```
**Action:**
- ❌ STOP immediately
- Report "Terminal is unresponsive"
- Do NOT commit
- User must restart terminal session

### Scenario D: Ambiguous Output
```
(unclear if passed or failed, mixed/confusing messages)
```
**Action:**
- ❌ STOP immediately
- Report "Build output is ambiguous"
- Re-run with plain output
- Do NOT commit until clear

### Scenario E: Coverage Below Threshold
```
Line Coverage: 65% (Target: 80%)
```
**Action:**
- ❌ STOP immediately
- Identify uncovered code
- User must write additional tests
- Rerun validation after tests added

### Scenario F: Flaky Test
```
[ERROR] TestFoo#testBar FAILED sometimes
```
**Action:**
- ❌ STOP immediately
- Identify test isolation issues
- User must fix test to be deterministic
- Run test 5x locally to verify before committing

## Tools & Commands

```bash
# Gate: Terminal responsiveness
echo "VALIDATION_TEST" && date

# Gate: Compilation
mvn clean compile

# Gate: Unit tests
mvn test

# Gate: Full verification
mvn verify

# Gate: Coverage report
mvn jacoco:report
open target/site/jacoco/index.html

# Gate: Exit code check
echo $?  # MUST be 0, anything else = FAILURE

# Check what changed
git status --short
git diff --cached

# Verify commit (after passing all gates)
git log -1 --stat
git show --stat
```

## Related Skills

- `commit-expert` — Use ONLY after this skill validates everything passes
- `java-modernizer` — Code cleanliness (no dead code)
- `tdd-expert` — Test design and test conventions
- `maven-management` — POM structure validation
