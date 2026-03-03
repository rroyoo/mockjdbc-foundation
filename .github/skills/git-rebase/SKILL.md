# Skill: Git Rebase & Conflict Resolution

## Context
Use this skill when a feature branch needs to stay synchronized with main, or when manual conflict resolution is needed during rebase. This skill ensures a clean, linear commit history while maintaining integration with main.

## When to Use This Skill
- **Trigger 1:** Feature branch has been open > 1 day and main has advanced
- **Trigger 2:** User says "rebase" or "update branch"
- **Trigger 3:** Pull fails with "branches have diverged"
- **Trigger 4:** Need to resolve merge conflicts during rebase
- **Trigger 5:** Before opening PR to ensure branch is up-to-date
- **Prerequisite:** Git is configured, currently on feature branch

## Core Principles

### 1. Why Rebase Instead of Merge?

**Rebase Benefits:**
- ✅ Linear, clean commit history
- ✅ Easier to understand feature progression
- ✅ Simpler to bisect if debugging
- ✅ No merge commits cluttering history
- ✅ Clear "when did this feature integrate?"

**Merge Drawbacks:**
- ❌ Creates merge commit clutter
- ❌ Non-linear history hard to follow
- ❌ Harder to understand true feature timeline
- ❌ Bisecting becomes complex

### 2. Periodic Rebase Workflow

**Frequency:** Every 1-2 days if main is actively evolving, or before opening PR.

#### Step A: Check Current State
```bash
git fetch origin  # Get latest main

# See what commits are on your branch
git log main..HEAD --oneline

# See if main has advanced
git log HEAD..origin/main --oneline

# Check branch status
git status
```

#### Step B: Rebase onto Latest Main
```bash
# Ensure you're on your feature branch
git branch --show-current  # Should show feature/{name}

# Rebase onto latest main
git rebase origin/main

# If successful: BUILD SUCCESS, no conflicts
# If conflicts: See "Conflict Resolution" section below
```

#### Step C: Verify Rebase Success
```bash
# Check commit history
git log main..HEAD --oneline  # Your commits should appear cleanly

# Verify build still passes
mvn clean verify  # MUST pass after rebase

# Check for any merge artifacts
git status  # Should be clean (nothing to commit)
```

#### Step D: Force-Push to Remote (Only After Rebase)
```bash
# IMPORTANT: Use --force-with-lease, NOT --force
# --force-with-lease is safer (respects concurrent pushes)
git push origin feature/{name} --force-with-lease

# Verify push succeeded
git branch -vv  # Should show up-to-date
```

### 3. Automatic Conflict Resolution Strategy

**Goal:** Resolve common conflicts automatically during rebase.

#### A) Detect Conflict Type
```bash
# During rebase, if conflicts occur:
git status  # Shows which files have conflicts

# Common conflict markers:
# <<<<<<<< HEAD
# Your changes
# ========
# Main changes
# >>>>>>>> origin/main
```

#### B) Auto-Resolve Common Patterns

**Pattern 1: Conflicting Imports (Java)**
```java
// BEFORE (conflict)
<<<<<<<< HEAD
import java.util.*;
import org.junit.jupiter.api.Test;
========
import java.util.*;
import org.mockito.Mock;
>>>>>>>> origin/main

// AFTER (resolved): Keep all unique imports
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
```
**Rule:** If conflicts are only in imports → combine unique imports, run `mvn compile` to verify.

**Pattern 2: Conflicting Properties (POM)**
```xml
// BEFORE (conflict)
<<<<<<<< HEAD
<maven.version.grpc>1.79.0</maven.version.grpc>
========
<maven.version.grpc>1.80.0</maven.version.grpc>
>>>>>>>> origin/main

// AFTER (resolved): Take main version (more recent)
<maven.version.grpc>1.80.0</maven.version.grpc>
```
**Rule:** If pom.xml version conflict → take main version (it's newer), verify with `mvn compile`.

**Pattern 3: Documentation Changes (markdown)**
```markdown
// BEFORE (conflict)
<<<<<<<< HEAD
## New Feature
Description of my feature
========
## Updated Section
Updated description
>>>>>>>> origin/main

// AFTER (resolved): Merge both sections
## New Feature
Description of my feature

## Updated Section
Updated description
```
**Rule:** If doc conflict → combine both, ensure logical order.

#### C) When to Abort & Manual Resolve
```bash
# If conflicts are complex (business logic, many files):
# ABORT rebase
git rebase --abort

# Report to user: "Complex conflicts detected, manual resolution needed"
# Exit rebase gracefully
```

### 4. Manual Conflict Resolution Process

**When automatic resolution fails:**

#### Step 1: Identify Conflict Files
```bash
git status  # Lists "both modified: ..." files
```

#### Step 2: Resolve Each Conflict
```bash
# Open each conflicting file
# Find conflict markers: <<<<<<<, =======, >>>>>>>

# Example conflict in Java:
public class Parser {
<<<<<<<< HEAD
  public void parseSQL(String sql) {  // My version
    // implementation 1
  }
========
  public void parseSQL(String sql) {  // Main version
    // implementation 2
  }
>>>>>>>> origin/main
}

# RESOLUTION: Choose the CORRECT version or combine them
# After deciding:
public class Parser {
  public void parseSQL(String sql) {
    // Final implementation (chosen or merged)
  }
}
```

#### Step 3: Mark Resolved
```bash
# After resolving each file:
git add {resolved-file}

# Do NOT commit yet (rebase handles it)
```

#### Step 4: Continue Rebase
```bash
# After ALL conflicts resolved and staged:
git rebase --continue

# If more conflicts appear: repeat steps 1-4

# If rebase completes:
# [main XXXX] Commit message
# No files changed (or shows clean history)
```

#### Step 5: Verify & Push
```bash
# Verify rebase succeeded
git log main..HEAD --oneline  # Your commits should appear cleanly

# Test build
mvn clean verify  # MUST pass

# Push with force-with-lease
git push origin feature/{name} --force-with-lease

# Verify
git branch -vv  # Should be up-to-date
```

### 5. Common Rebase Scenarios

#### Scenario A: Simple Rebase (No Conflicts)
```bash
git fetch origin
git rebase origin/main
# Success! No conflicts
mvn clean verify  # Passes
git push origin feature/{name} --force-with-lease
```

#### Scenario B: Rebase with Single Conflict
```bash
git fetch origin
git rebase origin/main
# Conflict in pom.xml
git status  # Shows "both modified: pom.xml"
# Edit pom.xml, resolve conflict
git add pom.xml
git rebase --continue
mvn clean verify  # Passes
git push origin feature/{name} --force-with-lease
```

#### Scenario C: Rebase with Multiple Conflicts
```bash
git fetch origin
git rebase origin/main
# Conflicts in Parser.java AND pom.xml
git status  # Shows both files

# Resolve Parser.java
git add Parser.java
git rebase --continue
# Conflict in pom.xml (on next commit)
git add pom.xml
git rebase --continue

# Success
mvn clean verify
git push origin feature/{name} --force-with-lease
```

#### Scenario D: Abort & Manual Help Needed
```bash
git fetch origin
git rebase origin/main
# Complex business logic conflicts

# If automatic resolution fails:
git rebase --abort  # Stop rebase

# Report to user: "Complex conflicts in [files]. Manual resolution needed."
# Wait for user to resolve and manually push
```

### 6. Anti-Patterns to Avoid

- ❌ **Using `git push --force` instead of `--force-with-lease`** — Dangerous, can overwrite concurrent pushes
- ❌ **Rebasing after PR is open** — Rewrites history, confuses reviewers. Only rebase BEFORE opening PR.
- ❌ **Rebasing interactive with `--interactive` without knowing commits** — Complex, easy to mess up
- ❌ **Leaving conflict markers unresolved** — Will cause build failures
- ❌ **Not testing after rebase** — May have broken tests due to integration
- ❌ **Merging instead of rebasing** — Creates clutter, defeats purpose

### 7. Troubleshooting

#### Problem: "Your branch has diverged from origin/main"
```bash
git fetch origin
git rebase origin/main  # Rebase to sync
# Or if you want to keep local commits separate:
git merge origin/main  # Merge (less preferred)
```

#### Problem: Rebase interrupted, "You are currently rebasing"
```bash
# Continue where left off:
git rebase --continue  # If conflicts resolved

# Or abort entire rebase:
git rebase --abort  # Start over
```

#### Problem: Accidentally rebased wrong branch
```bash
git reflog  # See recent HEAD movements

# Find the commit before rebase
git reset --hard {commit-hash}  # Go back

# Now rebase correctly
git rebase origin/main
```

#### Problem: After rebase, tests fail
```bash
# Likely a conflict resolution mistake
git log main..HEAD --oneline  # Review rebased commits

# Option A: Abort and resolve manually
git rebase --abort

# Option B: Fix the issue in conflicted commit
# (Edit file, add, continue)
git add {fixed-file}
git rebase --continue
```

#### Problem: Force-push rejected ("protected branch")
```bash
# Branch protection is enabled on remote

# Solution: Use --force-with-lease (already safer)
git push origin feature/{name} --force-with-lease

# If still rejected: Branch is protected, inform user
```

### 8. Integration with Periodic Updates

**Recommended Schedule:**
- Every morning: `git fetch origin && git rebase origin/main` (if main advanced)
- Before opening PR: Always rebase to ensure up-to-date
- Before final push: Validate build passes after rebase

**Automation (Future):**
- Could set up Git hooks to auto-rebase daily
- Or CI/CD that detects stale branches and notifies

## Related Skills

- `git-branching` — Creating and managing feature branches
- `build-quality` — Validate build passes after rebase
- `commit-expert` — Understand commit messages in rebased history
- `pull-request-expert` — Open PR only after successful rebase

## Commands Reference

```bash
# Fetch latest
git fetch origin

# Rebase on main
git rebase origin/main

# Check rebase status
git status  # Shows files with conflicts

# Resolve and continue
git add {resolved-files}
git rebase --continue

# Abort rebase
git rebase --abort

# Force-push after rebase
git push origin feature/{name} --force-with-lease

# View rebased commits
git log main..HEAD --oneline

# View commits in main not on your branch
git log HEAD..origin/main --oneline

# Check if branch is up-to-date
git branch -vv  # Should show "origin/main" or "[ahead N]"
```

## Git Configuration for Safe Rebase

```bash
# Set default push strategy to current branch
git config --global push.default current

# Use force-with-lease by default (safer than force)
git config --global push.force-with-lease true

# Auto-resolve common conflicts (if available)
git config --global merge.ours.driver "ours"

# Show helpful conflict markers
git config --global merge.conflictstyle diff3
```

