# Skill: Context-Aware Java Architect

## Context
Use this skill when the user asks to write, refactor, or review Java code. Instead of forcing a specific version, adapt your suggestions to the project's capabilities.

## Pre-Analysis Step
Before generating code:
1. **Detect Java Version:** Check `pom.xml`, `build.gradle`, or compiler settings to identify the Java version (e.g., 8, 11, 17, 21+).
2. **Feature Match:** Only use features supported by that version (e.g., don't suggest `records` if the project is on Java 11).

## Core Technical Mandates (Adaptive)

### 1. Functional & Clean Logic
- **Functional Priority:** Favor `Stream` API and `Optional` for data transformation.
- **Complexity Reduction:** Always use **Guard Clauses** (early returns) to eliminate `if-else` nesting and reduce cyclomatic complexity.
- **Naming:** Use highly descriptive variable names that reveal intent.

### 2. Code Cleanliness (Mandatory)
- **No Unused Imports:** Remove all unused `import` statements. Use IDE "Organize Imports" feature.
- **No Unused Variables:** Every variable must be used. Remove assignments that are never read.
- **No Commented Code:** Dead/commented-out code clutters the codebase. Delete it entirely.
  - **Exception:** Keep comments explaining *why* a workaround or non-obvious pattern exists (e.g., "Workaround for JDK-12345 bug in Java 17.0.1").
- **No Unused Private Methods/Fields:** Remove methods and fields that are never called or referenced.

### 3. Version-Specific Syntax (Apply only if supported)
- **Java 14+:** Use `records` for DTOs and data carriers.
- **Java 10+:** Use `var` for local variables where the type is obvious.
- **Java 17+:** Use `switch` expressions and pattern matching for `instanceof`.
- **Java 21+:** Use `String Templates` (if enabled) and `Sequenced Collections`.

### 4. Architecture & Performance
- **Big O Optimization:** Evaluate and optimize the time/space complexity of algorithms.
- **Patterns:** Implement Strategy or Factory patterns only if they simplify the logic.
- **SOLID:** Ensure strict adherence to Single Responsibility and Open/Closed principles.

### 5. Metrics & Observability
- When implementing metrics like `http_request_by(cos=xxx)`, ensure the logic is decoupled from the business domain (using Decorators, Proxies, or Interceptors).

## Anti-Patterns to Avoid

- ❌ **Unused Imports:** `import java.util.List;` but using `ArrayList` instead
- ❌ **Dead Variables:** `var unused = calculateValue();` where `unused` is never read
- ❌ **Commented Code:** `// this.oldMethod();` or `/* List<String> oldLogic = ... */`
- ❌ **Unused Methods:** Private methods with no callers
- ❌ **Unused Fields:** Class fields never accessed after initialization

## Quality Bar & Verification

Code is clean when:
- [ ] All imports are used (no orphaned `import` statements)
- [ ] All variables are used (no assignments discarded)
- [ ] No commented-out code (exception: explanatory comments preserved)
- [ ] No unused private methods or fields
- [ ] IDE warnings for unused code are resolved

## Validation Commands

```bash
# Check for unused imports/variables in Maven project
mvn clean compile -Dcompiler.useIncrementalCompilation=false

# IDE: Enable warnings for unused code
# IntelliJ: Settings > Editor > Inspections > Java > Declaration redundancy > Unused declaration
# Eclipse: Preferences > Java > Compiler > Errors/Warnings > Unnecessary code
```

## Instructions for the Agent
- "If you cannot determine the Java version, ask the user or assume the most stable modern version (Java 17) but mention this assumption."
- "Always run `mvn clean compile` after refactoring to catch any new compilation warnings."
- "Remove unused code before refactoring—cleanliness first, then features."
