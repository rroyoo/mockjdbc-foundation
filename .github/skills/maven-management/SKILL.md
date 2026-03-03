# Skill: Maven Project Management

## Context
Use this skill when designing or auditing Maven multi-module projects. This skill ensures consistent, maintainable POM structure across all modules, with centralized version management and clear dependency/plugin declarations.

## When to Use This Skill
- **Trigger 1:** Creating new Maven modules or projects
- **Trigger 2:** Reviewing or refactoring existing `pom.xml` files
- **Trigger 3:** Adding new dependencies or plugins to the project
- **Trigger 4:** Ensuring consistency across multi-module Maven builds

## Core Mandates & Rules

### 1. Centralized Version Management (Parent POM)
- **All versions live in `<dependencyManagement>` and `<pluginManagement>`** in the parent POM.
- Child modules declare dependencies **without versions** — they inherit from parent.
- Rationale: Single source of truth for versions, easier upgrades, prevents version conflicts.

**Pattern:**

```xml
<!-- Parent POM -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.example</groupId>
            <artifactId>example-lib</artifactId>
            <version>1.2.3</version>
        </dependency>
    </dependencies>
</dependencyManagement>

<pluginManagement>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.11.0</version>
        </plugin>
    </plugins>
</pluginManagement>

<!-- Child Module POM -->
<dependencies>
    <dependency>
        <groupId>org.example</groupId>
        <artifactId>example-lib</artifactId>
        <!-- Version inherited from parent -->
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <!-- Version inherited from parent -->
        </plugin>
    </plugins>
</build>
```

### 2. POM Element Order (Canonical Structure)

Every POM must follow this exact element order for consistency:

```xml
<project>
    <!-- 1. Declaration & Model -->
    <modelVersion>4.0.0</modelVersion>
    <groupId>...</groupId>
    <artifactId>...</artifactId>
    <version>...</version>
    <packaging>...</packaging>
    <name>...</name>
    <description>...</description>

    <!-- 2. Parent (if exists) -->
    <parent>...</parent>

    <!-- 3. Modules (parent only) -->
    <modules>...</modules>

    <!-- 4. Properties (ALPHABETICALLY SORTED) -->
    <properties>
        <a.property>...</a.property>
        <b.property>...</b.property>
        <z.property>...</z.property>
    </properties>

    <!-- 5. Dependency Management -->
    <dependencyManagement>
        <dependencies>...</dependencies>
    </dependencyManagement>

    <!-- 6. Dependencies -->
    <dependencies>...</dependencies>

    <!-- 7. Build (Plugins, Resources) -->
    <build>
        <pluginManagement>...</pluginManagement>
        <plugins>...</plugins>
        <resources>...</resources>
    </build>
</project>
```

### 3. Properties: Alphabetical Sorting

Sort all properties alphabetically by key name.

**Good:**
```xml
<properties>
    <java.version>17</java.version>
    <maven.compiler.release>${java.version}</maven.compiler.release>
    <maven.plugin.version.compiler>3.11.0</maven.plugin.version.compiler>
    <version.grpc>1.79.0</version.grpc>
    <version.junit>5.11.3</version.junit>
</properties>
```

**Bad:**
```xml
<properties>
    <version.grpc>1.79.0</version.grpc>
    <java.version>17</java.version>
    <maven.plugin.version.compiler>3.11.0</maven.plugin.version.compiler>
    <version.junit>5.11.3</version.junit>
</properties>
```

### 4. Property Naming Convention

Use consistent naming patterns for properties:

- `java.version` — JDK target version
- `maven.compiler.release` — Maven compiler target release
- `maven.plugin.version.{plugin-name}` — Maven plugin versions
- `maven.version.{artifact-name}` — External Maven artifact versions (libraries)
- `version.{domain}.{artifact}` — Alternative for external artifact versions (for clarity)

**Pattern:**
```xml
<properties>
    <java.version>17</java.version>
    <maven.compiler.release>${java.version}</maven.compiler.release>
    <maven.plugin.version.compiler>3.11.0</maven.plugin.version.compiler>
    <maven.version.junit>5.11.3</maven.version.junit>
    <maven.version.protobuf>4.34.0</maven.version.protobuf>
    <version.grpc>1.79.0</version.grpc>
</properties>
```

### 5. Scope Management

- `compile` (default) — Production code dependencies
- `test` — Testing-only dependencies (mark with `<scope>test</scope>`)
- `provided` — Dependencies provided by runtime container
- `runtime` — Only needed at runtime, not compile-time
- `import` — Bill of Materials (BOM) imports for dependency management

Always specify scope explicitly if not `compile`.

### 6. Dependency Order Within Sections

Within `<dependencies>` and `<dependencyManagement>`, order entries:
1. By scope: `compile`, then `test`, then `provided`, then `runtime`
2. Within scope: alphabetically by `groupId:artifactId`

### 7. Exclusions (Minimize, Document)

Use `<exclusions>` only when necessary to avoid transitive dependency conflicts.
Always document **why** an exclusion is needed.

**Example:**
```xml
<dependency>
    <groupId>org.example</groupId>
    <artifactId>legacy-lib</artifactId>
    <version>${maven.version.legacy}</version>
    <exclusions>
        <!-- Exclude old logging framework, using SLF4J instead -->
        <exclusion>
            <groupId>commons-logging</groupId>
            <artifactId>commons-logging</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

### 8. Module References (Parent POM Only)

In parent POMs, use `<module>` paths relative to the parent.

**Good:**
```xml
<modules>
    <module>module-a</module>
    <module>module-b</module>
    <module>integration-tests</module>
</modules>
```

**Bad:**
```xml
<modules>
    <module>../module-a</module>
    <module>./subdir/../module-b</module>
</modules>
```

## Anti-Patterns to Avoid

- ❌ **Version Hell:** Versions scattered across child POMs instead of parent `<dependencyManagement>`
- ❌ **Inconsistent POM Structure:** Elements in random order; different modules use different layouts
- ❌ **Unsorted Properties:** Properties in random order instead of alphabetical
- ❌ **Magic Strings:** Hardcoded version numbers in code or properties; use `<properties>` instead
- ❌ **Unused Dependencies:** Include dependencies without clear purpose
- ❌ **Broad Scopes:** Using `compile` scope for test-only libraries (use `test` scope)
- ❌ **Duplicate Versions:** Same version defined in multiple places
- ❌ **Poor Property Names:** Unclear naming that doesn't indicate version domain (e.g., `lib.version` is vague)

## Quality Bar & Verification

A Maven project is well-managed when:
- [ ] Parent POM contains all version definitions in `<dependencyManagement>` and `<pluginManagement>`
- [ ] All child modules declare dependencies **without versions**
- [ ] All POM files follow the canonical element order
- [ ] Properties are sorted alphabetically
- [ ] Property names follow naming conventions consistently
- [ ] All scopes are explicit (test, provided, runtime when applicable)
- [ ] No version conflicts detected: `mvn dependency:tree` shows single version per artifact
- [ ] Build is clean: `mvn clean verify` passes across all modules
- [ ] No duplicate or orphaned dependencies

## Validation Commands

```bash
# Check dependency tree for version conflicts
mvn dependency:tree

# Validate POM structure
mvn help:describe -Dplugin=help -Ddetail=true

# Analyze unused/undeclared dependencies
mvn dependency:analyze

# Full build validation
mvn clean verify
```

## Example Application

### Scenario: Refactoring Multi-Module Project

**Before (Chaotic):**
```xml
<!-- Parent POM - missing management sections -->
<project>
    <modules>
        <module>core</module>
        <module>api</module>
    </modules>
    <properties>
        <grpc.version>1.79.0</grpc.version>
        <java.version>17</java.version>
    </properties>
</project>

<!-- Core Module - versions hardcoded -->
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-protobuf</artifactId>
    <version>1.79.0</version>  <!-- ❌ HARDCODED -->
</dependency>
```

**After (Clean):**
```xml
<!-- Parent POM - centralized versions -->
<project>
    <modules>
        <module>core</module>
        <module>api</module>
    </modules>
    <properties>
        <java.version>17</java.version>
        <maven.compiler.release>${java.version}</maven.compiler.release>
        <version.grpc>1.79.0</version.grpc>
    </properties>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.grpc</groupId>
                <artifactId>grpc-protobuf</artifactId>
                <version>${version.grpc}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>

<!-- Core Module - inherits versions -->
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-protobuf</artifactId>
    <!-- ✅ VERSION INHERITED FROM PARENT -->
</dependency>
```

## Tips for Maven Success

1. **Parent as Single Source of Truth:** Treat parent POM as the contract for all versions; changes there cascade to all modules.
2. **Use Properties Liberally:** Define version properties for every external dependency and Maven plugin version.
3. **Test Dependency Tree:** Regularly run `mvn dependency:tree` to catch conflicts early.
4. **Consistent Formatting:** Use IDE plugins to auto-format XML (indent 4 spaces) to maintain consistency.
5. **Documentation:** Comment non-obvious exclusions and version constraints in POM.

