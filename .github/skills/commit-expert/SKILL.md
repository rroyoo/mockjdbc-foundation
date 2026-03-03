# Skill: Technical Commit Architect

## Context
Use this skill when the user asks to summarize changes or prepare a git commit message.

## Instructions
1.  **Analyze Diff:** Look at the staged changes or the current editor buffer.
2.  **Fowler Style:** Write messages that emphasize the "Intent" (why this change was made) over the "Mechanism" (what lines changed).
3.  **Structure:**
    * **Subject:** Use imperative mood (e.g., "Refactor...", "Introduce...", "Fix..."). Max 50 chars.
    * **Body:** Explain the rationale. If it's a refactoring, mention which "Code Smell" was removed.
    * **Metrics:** If the change includes your custom metrics like `http_request_by(cos=xxx)`, ensure the message explains the observability benefit.
4.  **Format:** Use Conventional Commits (feat:, fix:, refactor:, docs:).

## Example Output
`refactor: replace conditional with polymorphism in RequestHandler`
`The nested if-else was becoming a 'Long Function' smell. Introduced a Strategy pattern to handle different 'cos' types, improving the 'http_request_by' metric accuracy.`