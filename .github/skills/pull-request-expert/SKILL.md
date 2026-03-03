# Skill: Pull Request Management

## Context
Use this skill when opening a Pull Request (PR) to merge feature branches into `main`. This skill ensures PRs are high-quality, well-documented, and ready for review.

## ⚠️ MANDATORY: User Confirmation Required

**This skill MUST ONLY be activated when user explicitly requests it.**

### Valid Triggers (user must say one of these):
- "desarrollo completado"
- "feature ready" / "feature complete" / "feature finished"
- "bugfix ready" / "bugfix complete"
- "open PR" / "create PR" / "create pull request"
- "ready for review"
- "merge to main"

### Invalid Triggers (DO NOT activate):
- ❌ Feature branch exists but user hasn't confirmed completion
- ❌ All tests passing but user is still working
- ❌ Automatic activation after commits
- ❌ Assumption that work is done

**Rule:** Even if the branch looks complete, wait for explicit user confirmation before opening PR.

## When to Use This Skill

### Explicit Triggers (ONLY these):
- **Trigger 1:** User says "open PR" or "create pull request"
- **Trigger 2:** User says "development completed", "feature ready", or "bugfix complete"
- **Trigger 3:** User says "ready for review" or "merge to main"

### Prerequisites:
- Feature branch exists with commits
- All validations pass
- **User has explicitly confirmed work is complete**

## Core Principles

### 1. Pre-PR Checklist (MANDATORY - Never Skip)

**BEFORE opening any PR, MUST verify:**

```bash
# 0. Ensure GitHub CLI is authenticated
gh auth status || gh auth login

# 1. Branch is up-to-date with main
git fetch origin
git rebase origin/main  # NO conflicts

# 2. Working tree must be clean (no pending/untracked files)
git status --porcelain
test -z "$(git status --porcelain)"

# 3. All tests pass
mvn clean verify  # BUILD SUCCESS, 100% test pass

# 4. Code coverage adequate
mvn jacoco:report  # >=80% coverage

# 5. Build is green
mvn clean compile  # NO errors or warnings

# 6. Push branch to remote
git push origin feature/{feature-name}

# 7. Verify there are no local commits pending push
git fetch origin
git rev-list --count origin/feature/{feature-name}..HEAD
# MUST be 0 before opening PR

# 8. Verify remote contains your feature commits
git log origin/main..origin/feature/{feature-name} --oneline
```

**If any of these checks fails:**
- ❌ Do NOT open PR
- Fix/push/re-validate
- Retry only when all checks pass

### 2. PR Title Format

Follow Conventional Commits for title clarity:

```
<type>(<scope>): <description>
```

**Types:** `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`

**Examples:**
```
feat(adapter): rename MockQueryServiceAdapter to QueryServiceAdapter
fix(parser): handle null input in URL validation
refactor(connection): simplify property extraction logic
docs(readme): add configuration examples
test(integration): add end-to-end test suite
```

**Rules:**
- Max 72 characters (GitHub default truncation)
- Imperative mood ("Add", not "Added")
- Lowercase (except proper nouns)
- No period at end
- Should summarize the entire PR in one line

### 3. PR Description (Body)

Structure:

```markdown
## Summary
One-paragraph explanation of what this PR does and why.

## What Changed
- Bulleted list of specific changes
- Each bullet describes one logical change
- Link to related issues if applicable

## How to Test
Step-by-step instructions for reviewers to verify the changes work:
1. Step 1
2. Step 2
3. Verify expected outcome

## Checklist
- [ ] Tests added/updated
- [ ] Documentation updated
- [ ] No breaking changes
- [ ] Code coverage ≥80%
- [ ] Commits are logical and clean
- [ ] Build passes locally

## Related Issues
Fixes #123
Related to #456

## Notes for Reviewers
Any additional context or decisions made during development.
```

### 4. PR Description Examples

#### Feature PR
```markdown
## Summary
Implement gRPC-based query service adapter to resolve mocked queries. This replaces the legacy in-memory lookup with a network service call.

## What Changed
- Extract QueryServiceAdapter interface (transport-agnostic)
- Implement GrpcQueryServiceAdapter (gRPC-specific)
- Add proper gRPC channel lifecycle management with shutdown + awaitTermination
- Simplify property extraction logic (remove Optional nesting)
- Add comprehensive javadoc to both classes

## How to Test
1. Build the project: `mvn clean verify`
2. Run adapter tests: `mvn test -pl mockjdbc-mock -Dtest=GrpcQueryServiceAdapterTest`
3. Verify integration: `mvn verify -pl mockjdbc-mock -am`
4. Check logs for proper channel termination

## Checklist
- [x] Tests added (GrpcQueryServiceAdapterTest)
- [x] Documentation updated (javadoc)
- [x] No breaking changes (interface backward compatible)
- [x] Code coverage 92%
- [x] Commits are logical and clean (4 commits)
- [x] Build passes locally

## Related Issues
Relates to #42 (adapter refactoring initiative)

## Notes for Reviewers
This refactoring improves the interface design by:
1. Making transport mechanism explicit in class name (Grpc prefix)
2. Keeping interface transport-agnostic for future REST/in-memory implementations
3. Properly handling gRPC channel lifecycle (preventing resource leaks)
```

#### Bugfix PR
```markdown
## Summary
Fix null pointer exception in UrlMockDriverParser when handling URLs with missing optional properties.

## What Changed
- Add null-safety checks in property extraction
- Add defensive guards for optional parameters
- Add regression test for null input scenario

## How to Test
1. Run bugfix test: `mvn test -Dtest=UrlMockDriverParserTest#shouldHandleNullProperty`
2. Run all parser tests: `mvn test -pl mockjdbc-mock`
3. Verify no regression: `mvn verify`

## Checklist
- [x] Tests added (regression test)
- [x] Code coverage maintained at 88%
- [x] No breaking changes
- [x] Build passes locally
- [x] 2 clean commits

## Related Issues
Fixes #789 (null pointer in parser)

## Notes for Reviewers
The null handling is minimal and defensive — we only guard at the property extraction layer.
```

### 5. PR Workflow (Step by Step)

#### Step 0: Ensure GitHub CLI Authentication
```bash
# Check if authenticated
gh auth status

# If NOT authenticated, auto-login
if ! gh auth status >/dev/null 2>&1; then
  echo "GitHub CLI not authenticated. Running gh auth login..."
  gh auth login
  # Follow prompts:
  # 1. Select "GitHub.com"
  # 2. Select protocol (HTTPS/SSH - match your git remote)
  # 3. Authenticate via web browser
  # 4. After success, verify: gh auth status
fi
```

**Automated Authentication (for scripts):**
```bash
# One-liner to ensure authentication before PR
gh auth status || gh auth login
```

#### Step 1: Verify Branch is Ready
```bash
git checkout feature/{feature-name}
git fetch origin
git rebase origin/main  # Ensure up-to-date

# Must be clean before PR
test -z "$(git status --porcelain)"

# Push all commits
git push origin feature/{feature-name}

# Confirm nothing remains local
git fetch origin
test "$(git rev-list --count origin/feature/{feature-name}..HEAD)" -eq 0
```

#### Step 2: Validate Build
```bash
mvn clean verify  # MUST pass, BUILD SUCCESS
```

#### Step 3: Prepare PR Body from Template (MANDATORY)
```bash
# Use repository template as base
cp .github/pull_request_template.md /tmp/pr_body.md

# Edit /tmp/pr_body.md and fill all sections before creating PR
# (Summary, What Changed, How to Test, Checklist, Related Issues, Notes)
```

#### Step 4: Open PR via GitHub CLI (Automated)
```bash
# Ensure authenticated first
gh auth status || gh auth login

# Create PR using the filled template file
gh pr create \
  --base main \
  --head feature/{feature-name} \
  --title "feat(scope): description" \
  --body-file /tmp/pr_body.md
```

**Rule:** Do not use inline `--body` for non-trivial PRs; use `.github/pull_request_template.md` content via `--body-file`.

**Alternative: GitHub Web UI**
- Go to repository
- Click "New Pull Request"
- Select base: `main`, compare: `feature/{feature-name}`
- Fill title using Conventional Commits format
- Fill description using template above
- Click "Create Pull Request"

#### Step 5: Post-PR Steps
- Monitor CI/CD pipeline (if configured)
- Respond to reviewer comments
- Make requested changes on same branch
- Commits will automatically be added to PR

#### Step 6: Merge PR
Once approved:
```bash
# Option A: GitHub UI (Squash and merge)
# Option B: GitHub UI (Create a merge commit)
# Option C: CLI merge
git checkout main
git pull origin main
git merge --no-ff feature/{feature-name}  # Keep history
git push origin main
```

#### Step 7: Cleanup
```bash
git branch -d feature/{feature-name}  # Local
git push origin --delete feature/{feature-name}  # Remote
```

### 6. PR Review Collaboration

**When reviewer comments:**
1. Read feedback carefully
2. Ask clarifying questions if unclear
3. Make changes on the same branch
4. Do NOT squash/rewrite history (PR history is valuable)
5. Push commits (they auto-appear in PR)
6. Reply to comments with explanations
7. Request re-review once changes complete

**Guidelines:**
- Keep PRs focused (one feature per PR)
- Avoid mixing refactoring + features
- Keep PR size reasonable (< 400 lines changed is ideal)
- If PR gets too large, consider splitting into multiple PRs

### 7. Anti-Patterns to Avoid

- ❌ **Stale PRs** — Don't leave open for >3 days without activity
- ❌ **Large PRs** — >500 lines changed, hard to review
- ❌ **Mixed concerns** — Feature + refactoring + docs in one PR
- ❌ **No tests** — Every PR must include tests for changes
- ❌ **Unclear description** — Reviewers must understand intent
- ❌ **Force-pushing after PR is open** — Rewrites history, confuses reviewers
- ❌ **Merging without approval** — Always wait for review

### 8. Troubleshooting

#### Problem: "gh: command not found"
**Solution:** Install GitHub CLI.
```bash
# On Ubuntu/Debian
sudo apt install gh

# On macOS
brew install gh

# On other systems: https://cli.github.com/
```

#### Problem: "gh auth status" shows "not logged in"
**Solution:** Authenticate with GitHub.
```bash
# Interactive login
gh auth login

# Follow prompts:
# 1. Select "GitHub.com"
# 2. Select protocol (HTTPS recommended)
# 3. Authenticate via web browser
# 4. Verify: gh auth status
```

#### Problem: "gh auth login" hangs or fails
**Solution:** Use token authentication.
```bash
# Generate token at https://github.com/settings/tokens
# Select scopes: repo, workflow, read:org

# Login with token
gh auth login --with-token < ~/.github-token

# Or set environment variable
export GH_TOKEN=ghp_yourTokenHere
gh auth status  # Should show authenticated
```

#### Problem: "authentication required" when creating PR
**Solution:** Re-authenticate or refresh token.
```bash
# Logout and login again
gh auth logout
gh auth login

# Or refresh authentication
gh auth refresh -h github.com -s repo,workflow
```

#### Problem: "Merge conflict" message in PR
**Solution:** Rebase locally and resolve.
```bash
git fetch origin
git rebase origin/main  # Resolve conflicts
git push origin feature/{feature-name} --force-with-lease
```

#### Problem: PR has too many commits
**Solution:** Interactive rebase to squash/clean up.
```bash
git rebase -i main  # Squash related commits
git push origin feature/{feature-name} --force-with-lease
```

#### Problem: Reviewer requested changes
**Solution:** Make changes and push (auto-updates PR).
```bash
# Edit files and commit
git commit -m "review: address feedback on error handling"
git push origin feature/{feature-name}
```

#### Problem: PR is taking too long to review
**Solution:** Ping reviewer or escalate.
```bash
# Add comment: "@reviewer, is there anything else needed for approval?"
```

## Integration with Other Skills

- **Before opening PR:** Validate with `build-quality` skill
- **Branch creation/management:** Use `git-branching` skill
- **Commit messages:** Follow `commit-expert` skill
- **Code quality:** Ensure `java-modernizer` rules followed

## GitHub PR Template (Optional)

Create `.github/pull_request_template.md`:
```markdown
## Summary
Brief description of changes.

## What Changed
- Change 1
- Change 2

## How to Test
Steps to verify.

## Checklist
- [ ] Tests added
- [ ] Documentation updated
- [ ] Build passes locally
- [ ] Code coverage ≥80%

## Related Issues
Fixes #XXX
```
