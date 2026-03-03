# Skill: Git Branching & Feature Workflow

## Context
Use this skill when starting a new feature, bugfix, or research branch. This skill manages the entire Git branching lifecycle, from creation through merge preparation.

## 🔴 CRITICAL RULES (NEVER VIOLATE)

1. **ALWAYS fetch before creating branch:**
   ```bash
   git fetch origin  # MANDATORY first step
   ```

2. **ALWAYS create branches from main:**
   ```bash
   git checkout main
   git pull origin main
   git checkout -b feature/{name}
   ```

3. **NEVER create branch from another feature branch**
   - ❌ Wrong: `git checkout feature/A && git checkout -b feature/B`
   - ✅ Right: `git checkout main && git pull && git checkout -b feature/B`

**Standard Branch Creation Workflow (use this every time):**
```bash
git fetch origin                    # 1. Fetch latest from remote
git checkout main                   # 2. Switch to main
git pull origin main                # 3. Update main to latest
git checkout -b feature/{name}      # 4. Create branch from current main
```

## ⚠️ MANDATORY ACTIVATION (NEVER Skip)

**This skill MUST be activated BEFORE any code changes if:**
- User asks to implement/add/create anything (feature, functionality, component)
- User asks to fix/correct/debug anything (bugfix)
- User asks to refactor/improve/optimize code
- User asks to update/change documentation
- User asks to add/modify tests
- **ANY task that will result in code changes**

**Decision Logic:**
```
IF (task involves code/doc/test changes) THEN
  1. Activate git-branching skill FIRST
  2. Create appropriate branch
  3. THEN make changes
ELSE
  Only queries/read-only operations allowed on main
END IF
```

## When to Use This Skill

### Automatic Triggers (ALWAYS activate):
- **Trigger 1:** User says "implement", "add", "create" → Create `feature/{name}` branch
- **Trigger 2:** User says "fix", "correct", "debug" → Create `bugfix/{name}` branch
- **Trigger 3:** User says "refactor", "improve", "optimize" → Create `refactor/{name}` branch
- **Trigger 4:** User says "document", "add docs" → Create `docs/{name}` branch
- **Trigger 5:** User says "add tests", "test coverage" → Create `test/{name}` branch
- **Trigger 6:** User says "update dependencies", "upgrade" → Create `chore/{name}` branch

### Manual Triggers:
- User explicitly says "start feature X" or "create branch Y"
- Need to switch between existing branches
- Need to verify current branch status

### Anti-Trigger (DO NOT activate):
- User asks questions ("what is X?", "how does Y work?")
- User asks to review/read code
- User asks to explain architecture
- Read-only operations

## Core Principles

### 1. Branch Naming Convention
Follow conventional naming for clarity:

```
feature/{feature-name}     # New feature (e.g., feature/user-authentication)
bugfix/{bug-name}          # Bug fix (e.g., bugfix/null-pointer-exception)
refactor/{refactor-name}   # Refactoring (e.g., refactor/simplify-parser)
docs/{doc-name}            # Documentation (e.g., docs/api-guide)
test/{test-name}           # Test improvements (e.g., test/coverage-increase)
chore/{task-name}          # Maintenance (e.g., chore/update-dependencies)
```

**Rules:**
- Use lowercase
- Use hyphens to separate words
- Keep names descriptive but concise (< 40 chars)
- No spaces or special characters
- Start from `main` (never from another feature branch)

### 2. Branch Lifecycle

#### A) Create Branch (ALWAYS from main)

**MANDATORY steps (NEVER skip):**

```bash
# 1. Fetch latest changes from remote (ALWAYS first)
git fetch origin

# 2. Switch to main branch
git checkout main

# 3. Update main to latest
git pull origin main

# 4. Create and switch to new branch from current main
git checkout -b feature/{feature-name}

# 5. Verify branch was created correctly
git branch --show-current
```

**Rules:**
- ⚠️ **ALWAYS fetch first** (`git fetch origin`)
- ⚠️ **ALWAYS create from main** (never from another feature branch)
- ⚠️ **ALWAYS update main** before creating branch (`git pull origin main`)
- ⚠️ If you're not on main, switch to it first (`git checkout main`)

**Verification:**
- Current branch shows new feature name
- Branch tracking: `git branch -vv` shows it's based on main
- Main is up-to-date: `git log origin/main..main` shows nothing

#### B) Work on Branch
- Make commits following `commit-expert` skill
- MUST validate each commit with `build-quality` skill
- MUST follow `tdd-expert` conventions for tests
- MUST follow `java-modernizer` for code cleanliness
- MUST follow `maven-management` for POM changes

#### C) Keep Branch Updated
If main gets updated during your work:
```bash
# Fetch latest changes
git fetch origin

# Rebase onto latest main (preferred over merge)
git rebase origin/main

# If conflicts occur, resolve them and continue
git rebase --continue
```

### 3. Pre-PR Validation (Before Opening Pull Request)

**MANDATORY CHECKS before opening PR:**

1. ✅ **Branch is up-to-date with main**
   ```bash
   git fetch origin
   git rebase origin/main
   # No conflicts
   ```

2. ✅ **All commits on branch are clean**
   ```bash
   git log main..HEAD --oneline  # See your commits
   # Each commit has clear message + intent
   ```

3. ✅ **Build is GREEN**
   ```bash
   mvn clean verify  # MUST pass
   ```

4. ✅ **Tests PASS**
   ```bash
   mvn test  # 100% pass rate
   ```

5. ✅ **Coverage is adequate**
   ```bash
   mvn jacoco:report  # ≥80% or better
   ```

6. ✅ **No unused code**
   - Per java-modernizer: no dead imports/variables
   - No commented-out code

7. ✅ **Code review ready**
   - Clear commit messages
   - Logical commit history (no "WIP" or "fix" commits)
   - No merge commits (rebase, don't merge)

### 4. Branch Cleanup

After PR is merged:
```bash
# Delete local branch
git branch -d feature/{feature-name}

# Delete remote branch (GitHub will prompt, or manual)
git push origin --delete feature/{feature-name}

# Verify it's deleted
git branch -a  # Should not show the deleted branch
```

## Common Workflows

### Scenario A: Simple Feature (Few Commits)
```bash
# 1. ALWAYS fetch first
git fetch origin

# 2. Start from main
git checkout main
git pull origin main

# 3. Create branch
git checkout -b feature/add-logger

# Work and commit (1-3 commits)
git add src/...
git commit -m "feat(logging): add structured logging to Parser"

# Keep updated with main
git fetch origin
git rebase origin/main

# Ready for PR
```

### Scenario B: Complex Feature (Multiple Commits)
```bash
# 1. ALWAYS fetch first
git fetch origin

# 2. Start from main
git checkout main
git pull origin main

# 3. Create branch
git checkout -b feature/redesign-api

# Work on multiple areas (5+ commits)
git commit -m "feat(api): extract common interfaces"
git commit -m "feat(api): implement factory pattern"
git commit -m "refactor(api): remove legacy adapters"
git commit -m "test(api): add integration tests"

# Keep synced periodically
git fetch origin
git rebase origin/main  # If main advanced

# Clean up history if needed (interactive rebase)
git rebase -i main  # Squash related commits if needed

# Ready for PR
```

### Scenario C: Bugfix
```bash
# Create bugfix branch
git checkout -b bugfix/null-pointer-in-parser

# Make targeted fix
git commit -m "fix(parser): handle null input safely"
git commit -m "test(parser): add regression test for null input"

# Ready for PR with 2 clean commits
```

## Branch Status Commands

```bash
# Current branch
git branch --show-current

# All branches (local + remote)
git branch -a

# See branch tracking
git branch -vv

# See commits on your branch (not in main)
git log main..HEAD --oneline

# See commits in main not on your branch
git log HEAD..origin/main --oneline

# See files changed on your branch
git diff main..HEAD --name-only

# See diff from main
git diff main..HEAD
```

## Troubleshooting

### Problem: "Your branch is ahead of origin/main by 5 commits"
**Solution:** Your branch has commits not pushed yet. This is OK if you're still working.
```bash
git push origin feature/{feature-name}  # Push to remote
```

### Problem: "Your branch and origin/main have diverged"
**Solution:** main advanced. Rebase onto it.
```bash
git fetch origin
git rebase origin/main  # Resolve conflicts if needed
git push origin feature/{feature-name} --force-with-lease  # Update remote
```

### Problem: Merge conflicts during rebase
**Solution:** Resolve conflicts, then continue.
```bash
# Edit conflicting files and resolve conflicts
git add {resolved-files}
git rebase --continue  # Not --merge!
```

### Problem: Accidental commits on main
**Solution:** Move commits to feature branch.
```bash
# 1. FIRST, fetch to ensure you have latest main
git fetch origin

# 2. See recent commits on main
git log main --oneline -5

# 3. Undo last commit, keep changes
git reset --soft HEAD~1

# 4. Create feature branch from current main
git checkout -b feature/{new-branch}

# 5. Commit the changes
git commit -m "feat: ..."

# 6. Push to remote
git push origin feature/{new-branch}
```

### Problem: Need to create branch but unsure if main is current
**Solution:** Always fetch first.
```bash
# This is the STANDARD workflow (always use this)
git fetch origin
git checkout main
git pull origin main
git checkout -b feature/{new-feature}
```

## Anti-Patterns to Avoid

- ❌ **Direct commits to main** — Always use feature branch
- ❌ **Merge commits in history** — Use rebase to keep linear history
- ❌ **Long-lived branches** — Keep < 1 week, small scope
- ❌ **Stale branches** — Rebase regularly, don't let main advance too far
- ❌ **Unclear branch names** — Use conventional naming
- ❌ **Commits without tests** — Always add tests for features
- ❌ **Pushing uncommitted changes** — Stage + commit before pushing

## Integration with Other Skills

- **Before committing on branch:** Use `build-quality` skill to validate
- **Writing commit messages:** Use `commit-expert` skill
- **Code quality:** Ensure `java-modernizer` rules followed
- **Tests:** Follow `tdd-expert` conventions
- **Opening PR:** Use `pull-request-expert` skill (complementary)

## Git Configuration Recommendations

```bash
# Set up rebase as default for pulls
git config --global pull.rebase true

# Use force-with-lease instead of force (safer)
git config --global push.default current

# Show branch tracking in status
git config --global status.showUntrackedFiles all

# Color output for better readability
git config --global color.ui auto
```

