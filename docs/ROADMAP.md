# VEbalist implementation roadmap

Tasks are proposed, not proof of completion. Update statuses only with verified evidence.

| Priority | Task | Acceptance criteria | Status |
|---|---|---|---|
| P0 | Verify backend CI | Unit tests run in GitHub Actions and show passing status | Verified: run 42014833680 passed on branch head `a15d93b` |
| P0 | Audit Android client | Identify actual API caller and version; verify compatibility with protected endpoints | In progress: missing shared-key auth identified; confirmation and shipping payload gaps addressed; safe auth remains |
| P0 | Audit web application | Locate source/deployment; verify server-side auth proxy and no browser-embedded secrets | Not implemented: Replit workspace is a placeholder; no active backend calls, auth flow, or publish UI |
| P0 | Review security PR | Confirm API key strategy, confirmation flow, and staging behavior before merge | In progress: PR #5 draft |
| P1 | Staging backend | Build image, configure runtime secret references, smoke-test endpoints | Not started; owner approval needed for deployment |
| P1 | Listing draft pipeline | Multi-photo identification, title, description, category, aspects, suggested price | Not started in audited backend |
| P1 | Shipping and exports | Editable packed weight/dimensions, CSV/JSON exports, validation | Partially implemented: Android imports and validates packed measurements; editor controls and end-to-end remain |
| P1 | Photo preparation | Preserve originals, produce optional white-background copies | Not verified |
| P2 | Release readiness | End-to-end Android/web tests, rollback plan, owner sign-off | Not started |

## Release gates
1. CI passing and critical paths tested.
2. No secrets in repository or client bundles.
3. Android and web integrations validated against staging.
4. Owner explicitly approves production deployment.
5. Each live eBay listing requires user review and explicit publish confirmation.
