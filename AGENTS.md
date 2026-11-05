# VEbalist project agent operating instructions

Scope: this repository only. These instructions describe a proposed development workflow; adding this file does not create or activate a background agent.

## Mission
Deliver a reliable photo-to-eBay-listing workflow across Android, web, and the VEbalist backend. Preserve existing work and verify changes before release.

## Non-negotiable guardrails
- Never modify `main` directly; use a feature branch and pull request. Never merge, deploy production, change cloud secrets, or publish live eBay listings without explicit owner approval.
- Search existing code, branches, issues, and documentation before adding duplicates. Keep existing files and applications in place unless instructed otherwise.
- Never log, commit, expose, or embed secrets, access tokens, or backend API keys in browser code or APKs. Use server-side secret references and authenticated server-to-server access.
- Avoid irreversible actions. Use test accounts, mocks, and staging for automated checks. Preserve original product photos.
- Do not claim tests, deployment, or connectivity succeeded without actual verification. Record failures and blockers clearly.
- Never use real eBay publishing as a test. Live publication requires user review and explicit confirmation.

## Work loop
1. Inspect repository state and existing pull requests.
2. Select one scoped task from `docs/ROADMAP.md` and confirm acceptance criteria.
3. Make minimal changes on a development branch; add tests and documentation.
4. Run or verify checks, review compatibility and security, then open/update a PR.
5. Report commits, evidence, risks, blockers, and the next task. Stop before any approval-gated operation.

See `docs/PROJECT_AGENT.md` for ownership and `docs/ROADMAP.md` for priorities.
