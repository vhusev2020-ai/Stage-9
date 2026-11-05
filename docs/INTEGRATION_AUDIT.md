# VEbalist integration audit

**Branch:** `security-and-tests-2026-11-05`  
**Reviewed:** 2026-11-05  
**Scope:** Backend authentication, Android integration, Replit web app, and CI. No deployment or eBay publishing.

## Current implementation

- Backend API authentication now accepts either a verified Google ID token for an email in `VEBALIST_ALLOWED_GOOGLE_EMAILS`, or `X-API-Key` for trusted server-to-server calls. Google token verification checks its signature, issuer, audience, and verified email. Missing auth configuration fails closed.
- Android uses Credential Manager to sign in, stores the ID token in memory only, and sends it as a Bearer token to protected backend routes. The shared API key is not included in Android.
- Android retains imported packed weight/dimensions, sends them for validation and publishing, and requires an explicit confirmation dialog before submitting a publish request.
- The backend still owns eBay OAuth credentials. Android and browser clients do not receive those credentials.

## Web app findings

- Read-only inspection of `@vslife009/Listing-Automator` found a placeholder UI, no active VEbalist backend calls, no authentication flow, and no publish UI or implemented publishing endpoint. The app's API specification described routes that were not implemented. No shared backend or eBay credentials were found in the inspected workspace; a separately deployed version was not inspected.
- A Replit source update has been requested to build the web client with Google sign-in and a same-origin server proxy that forwards the user's ID token to the backend. The update is still in progress; its result has not been verified or published.

## Verification

- GitHub Actions backend tests passed on authentication commit `75b19c5` (run `42018029479`).
- The Android debug APK built and uploaded successfully on authentication commit `75b19c5` (run `42018029357`).
- Local backend tests pass **15/15**, including mocked Google-token acceptance, rejected identities, invalid tokens, missing configuration, API-key calls, confirmation gating, and payload checks.
- A Google OAuth client ID and allowlisted email are not configured here, and no live Google token was used.
- No production deployment or live eBay API publishing was performed.

## Remaining work

1. Configure a Google OAuth web client ID and the exact allowed email on the backend runtime; supply the public client ID to the Android build. This has not been configured in any cloud environment.
2. Verify the Replit update result and configure the public OAuth client ID and backend URL there; keep all shared keys and eBay secrets server-side.
3. Add editor controls for packed weight and dimensions, then run staging integration tests before considering a merge.
