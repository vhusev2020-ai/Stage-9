# VEbalist — GitHub Copilot development handoff

**Prepared:** 2026-11-05  
**Repository:** `vhusev2020-ai/Stage-9`  
**Existing delegation issue:** https://github.com/vhusev2020-ai/Stage-9/issues/6  
**Status:** Ready for human-triggered Copilot assignment; not evidence of an active Copilot agent.

## Mission
Produce a tested integration candidate that preserves VEbalist Android 2.0.5 listing features while adopting secure Google ID-token authentication and backend publishing safeguards. Do not replace or move existing work.

## Read first
1. `AGENTS.md`, `docs/PROJECT_AGENT.md`, `docs/ROADMAP.md`, `docs/INTEGRATION_AUDIT.md`, and `PRODUCTION_CHECKLIST.md`.
2. Existing PR #3 (`agent/vebalist-2-0-5-workflow`) and PR #5 (`security-and-tests-2026-11-05`). Check their current head commits rather than relying on previously recorded SHAs.
3. Issue #6 and any current comments. Search other issues/branches for overlapping tasks before editing.

## Verified and reported state
- `security-and-tests-2026-11-05` is 23 commits ahead of `main` in the latest GitHub comparison; `main` remains at `d4a03bd6` in that comparison.
- Backend smoke tests passed on GitHub Actions run 42014833680; `python -m unittest discover -p 'test_*.py' -v` succeeded in the workflow.
- `docs/INTEGRATION_AUDIT.md` reports a separate successful Android debug build and 15/15 local backend tests; check current CI and build artifacts independently.
- The audit reports Android Google ID-token sign-in and bearer authorization in the security branch, with Google OAuth runtime configuration outstanding.
- The audit describes the Replit web update as requested, not verified or published. Issue #6 says web-client work is happening in `vhusev2020-ai/Listing-Automator`; verify its current state and do not modify that repository as part of this assignment.
- No verified staging end-to-end flow, production deployment, or live eBay publishing has been established by this handoff.

## First assignment: integrate Android workflow and security
1. Compare PR #3 and PR #5, including Android code, backend request schemas, build workflows, and deployment automation.
2. Create a **new** integration branch from a suitable base. Preserve both source branches and `main`; open a draft PR targeting `main` and clearly document merge strategy.
3. Preserve ZIP batch import/export, shipping weight and dimension editors, original photos, resumable review, and explicit user approval before publishing.
4. Preserve secure Google ID-token verification, exact email allowlist, in-memory Android tokens, and server-side-only secrets. Do not embed `VEBALIST_API_KEY` or eBay credentials in Android or browser code.
5. Document precise API contracts: authentication headers, validation payload, `confirm_publish: true`, errors, item specifics, packed weight and dimensions, photos, and exported formats.
6. Add regression tests for import/export, duplicate SKUs, confirmation, invalid measurements, unauthorized calls, and account mismatch where feasible.
7. Run backend unit tests and Android debug build; report exact commands, workflow URLs, pass/fail evidence, and blockers.
8. Keep any automatic production deployment disabled in the candidate. Do not deploy, merge, modify secrets, or publish real listings.

## Acceptance criteria
- Draft integration PR exists and links Issue #6, PR #3, and PR #5.
- Android compilation and backend tests have verified passing runs or accurately documented blockers.
- Existing 2.0.5 user workflow is preserved, with any exceptions explicitly enumerated.
- Authentication and publishing require explicit user authorization; secrets remain server-side.
- A reproducible staging test plan and owner approval gates are documented.

## Human approvals
Repository edits on development branches and draft PRs are allowed. **Separate explicit owner approval is required** before merging, production deployment, modifying cloud credentials/infrastructure, incurring costs, or publishing/updating live eBay listings.

## Reporting format
Summarize completed changes, files/commits/PR, tests and links, unresolved risks, decisions requiring owner approval, and next action. Never mark unverified work complete.
