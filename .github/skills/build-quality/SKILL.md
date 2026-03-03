# Skill: Build Quality Assurance

## Context
Use this skill when you need to validate code quality before committing. This skill ensures compilation, test execution, code coverage analysis, and static analysis checks are performed consistently. Enforce a "green build" before any commit.

## When to Use This Skill
- **Trigger 1:** User says "I'm ready to commit" or "commit my changes"
- **Trigger 2:** Code has been modified and needs validation
- **Trigger 3:** You need to verify the project state before integration
- **Trigger 4:** User mentions "build", "test", "verify", "commit"
- **Prerequisite:** Maven or Gradle build tool configured in project

## Core Principles & Guidelines

### 1. Pre-Commit Validation Checklist
Before ANY commit, execute this sequence:
```bash
# Step 1: Clean and compile
mvn clean compile

# Step 2: Run all unit tests
mvn test

# Step 3: Run integration tests and verify
mvn verify

# Step 4: Check code coverage (≥80% for modified code)
mvn jacoco:report

# Step 5: Optional static analysis
mvn spotbugs:check pmd:check
```

### 2. Build State Requirements
- ✅ **Green Build:** All tests pass, no compilation errors
- ✅ **Code Coverage:** ≥80% for business logic, 100% for critical paths
- ✅ **No Warnings:** Resolve compiler warnings or document exceptions
- ✅ **Deterministic:** Build passes consistently, no flaky tests

### 3. Fail-Fast Approach
- Stop immediately if compilation fails
- Do NOT proceed if tests fail
- Report first failure clearly (not all failures at once if possible)
- Provide actionable error messages

### 4. Module-Level Validation (for multi-module projects)
- Validate only affected modules when possible: `mvn -pl {module-name} -am clean verify`
- Full build validation before final commit: `mvn clean verify`

## Step-by-Step Workflow

1. **Compile Check:** Run `mvn clean compile`. Fix compilation errors immediately.
2. **Unit Test Execution:** Run `mvn test`. All tests must pass.
3. **Integration Test Execution:** Run `mvn verify`. All integration tests must pass.
4. **Coverage Analysis:** Run `mvn jacoco:report`. Review coverage report.
5. **Static Analysis:** Optionally run `mvn spotbugs:check pmd:check`. Fix high-priority violations.
6. **Green Build Confirmation:** Confirm all steps passed.
7. **Commit:** Proceed only if all validations are green.

## Common Build Failure Scenarios

### Compilation Failure
```
[ERROR] COMPILATION ERROR: ...
```
**Action:** 
1. Review error message carefully
2. Fix the source code
3. Recompile: `mvn clean compile`
4. Don't proceed until compilation succeeds

### Test Failure
```
[ERROR] Tests run: 5, Failures: 1, Errors: 0
```
**Action:**
1. Review failing test output
2. Determine if test or implementation is wrong
3. Fix the issue
4. Rerun: `mvn test`
5. Ensure ALL tests pass

### Coverage Below Threshold
```
Line Coverage: 65% (Target: 80%)
```
**Action:**
1. Identify uncovered code
2. Write additional unit tests
3. Rerun: `mvn jacoco:report`
4. Verify coverage meets target

### Intermittent / Flaky Test
```
[ERROR] TestFoo#testBar FAILED (passes locally, fails in CI)
```
**Action:**
1. Identify test isolation issues (shared state, timing)
2. Fix test to be deterministic
3. Run test multiple times locally to verify
4. Commit once test is consistently passing

## Anti-Patterns to Avoid

- ❌ **Committing Without Running Tests:** "I'll test it later"
- ❌ **Ignoring Build Warnings:** Warnings often hide bugs
- ❌ **Skipping Coverage:** Low coverage = high risk of regressions
- ❌ **Flaky Tests:** Intermittently failing tests break trust in build
- ❌ **Partial Builds:** Validating only one module when multi-module dependencies exist
- ❌ **Build Cache Issues:** Not cleaning before critical builds (use `mvn clean`)
- ❌ **Ignoring CI Failures:** Local green build ≠ CI green build; understand environment differences

## Quality Bar & Verification

A build is production-ready when:
- [ ] `mvn clean compile` succeeds (no errors)
- [ ] `mvn test` passes (all unit tests green)
- [ ] `mvn verify` passes (all integration tests green)
- [ ] Code coverage ≥80% for business logic
- [ ] No compiler warnings (or documented exceptions)
- [ ] No flaky tests (deterministic, <100ms per test)
- [ ] Static analysis passes (spotbugs, pmd, or equivalent)
- [ ] Build output is clean (no unexpected messages)

## Example Application

### Pre-Commit Validation Flow

```bash
# User says: "I'm done with my changes, let's commit"

# 1. Check compilation
$ mvn clean compile
[INFO] BUILD SUCCESS ✅

# 2. Run unit tests
$ mvn test
[INFO] Tests run: 42, Failures: 0, Errors: 0
[INFO] BUILD SUCCESS ✅

# 3. Run integration tests
$ mvn verify
[INFO] All integration tests passed ✅
[INFO] BUILD SUCCESS ✅

# 4. Check coverage
$ mvn jacoco:report
[INFO] Line Coverage: 82% ✅

# 5. Confirm all green
[INFO] All checks passed! Ready to commit.

# 6. Proceed with commit
$ git commit -m "refactor: ..."
```

### Handling a Test Failure

```bash
$ mvn test
[ERROR] TestOrderService#testShouldRejectNullCustomerId FAILED

# Review the failure
[ERROR] Expected: IllegalArgumentException
[ERROR] Actual: NullPointerException

# Fix the issue (test or implementation)
# Update code...

# Rerun tests
$ mvn test
[INFO] Tests run: 42, Failures: 0, Errors: 0
[INFO] BUILD SUCCESS ✅

# Now safe to commit
```

## Maven Command Reference

| Command | Purpose |
|---------|---------|
| `mvn clean compile` | Clean, compile sources |
| `mvn test` | Run unit tests only |
| `mvn verify` | Full build + integration tests |
| `mvn jacoco:report` | Generate coverage report |
| `mvn spotbugs:check` | Check for potential bugs |
| `mvn pmd:check` | Check code style and issues |
| `mvn -pl {module} -am clean verify` | Validate single module and dependencies |

## Tips for Build Success

1. **Run Locally Before Pushing:** Never rely solely on CI to catch failures.
2. **Understand Build Environment:** Local Java version, Maven version, OS differences can affect builds.
3. **Use Build Profiles:** For different environments (dev, test, prod).
4. **Cache Management:** Clear `.m2` cache if experiencing weird failures: `rm -rf ~/.m2/repository`
5. **Fail Fast:** Address the first error immediately; subsequent errors may be cascades.


