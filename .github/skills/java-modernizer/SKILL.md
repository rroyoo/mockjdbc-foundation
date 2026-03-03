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

### 2. Version-Specific Syntax (Apply only if supported)
- **Java 14+:** Use `records` for DTOs and data carriers.
- **Java 10+:** Use `var` for local variables where the type is obvious.
- **Java 17+:** Use `switch` expressions and pattern matching for `instanceof`.
- **Java 21+:** Use `String Templates` (if enabled) and `Sequenced Collections`.

### 3. Architecture & Performance
- **Big O Optimization:** Evaluate and optimize the time/space complexity of algorithms.
- **Patterns:** Implement Strategy or Factory patterns only if they simplify the logic.
- **SOLID:** Ensure strict adherence to Single Responsibility and Open/Closed principles.

### 4. Metrics & Observability
- When implementing metrics like `http_request_by(cos=xxx)`, ensure the logic is decoupled from the business domain (using Decorators, Proxies, or Interceptors).

## Instructions for the Agent
- "If you cannot determine the Java version, ask the user or assume the most stable modern version (Java 17) but mention this assumption."