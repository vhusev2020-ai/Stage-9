# VEbalist Project Agent Charter

**Status:** Proposed operating structure; not an installed or autonomous background service.
**Owner:** Human product owner, who approves production releases and live eBay publishing.
**Source of truth:** Existing GitHub repository `vhusev2020-ai/Stage-9`. Do not relocate work.

## Responsibilities
- Inventory existing Android, web, backend, CI, and deployment assets; reconcile code versions with actual releases.
- Maintain prioritized issues, small PRs, test evidence, and a release checklist.
- Coordinate integration with the existing Replit Listing Automator and Google Cloud Run backend after access is verified.
- Build photo upload, AI-assisted listing draft, editable review, pricing guidance, item specifics, package weight/dimensions, original-photo preservation, and CSV/JSON export.
- Require a human confirmation step before any real eBay publication.

## Authority boundaries
**Allowed with repository access:** Read code, document findings, create development branches, write tests, commit scoped changes, open draft PRs.
**Requires owner approval:** Merge into main, deploy to production, modify credentials or cloud infrastructure, delete/migrate assets, incur costs, or publish/update live eBay listings.
**Never allowed:** Commit secrets; place a shared API key in client-side code; claim an unverified deployment works.

## Current evidence (2026-11-05)
- Existing development PR: https://github.com/vhusev2020-ai/Stage-9/pull/5
- Backend Flask endpoints include status, account setup, aspects, validation, and batch publishing.
- Development branch adds API-key authentication, confirmation gate, validation checks, tests, and a Cloud Run container definition.
- Tests and live deployment are **not yet verified**. Android and web clients may require authentication changes.
- Replit connector access returned an authorization error. The original app under `@vslife009` has not been verified through the connector.

## Every work report must include
Scope; files changed; commit/PR; tests actually executed and results; remaining risks; owner approvals required; next action.
