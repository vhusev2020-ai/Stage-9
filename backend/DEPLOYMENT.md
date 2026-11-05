# VEbalist backend — deployment checklist (development branch)

This directory can be built as a container using `backend/Dockerfile` (set the build context to `backend`). No production deployment is performed by this repository change.

## Before deployment

1. Confirm backend unit tests pass: `python -m unittest discover -p 'test_*.py' -v` from `backend`.
2. Configure `VEBALIST_API_KEY` as a runtime secret for trusted server-to-server callers only. Never put it in browser JavaScript, a mobile APK, build arguments, or the container image.
3. Supply eBay OAuth credentials through runtime secret references: `EBAY_CLIENT_ID`, `EBAY_CLIENT_SECRET`, `EBAY_REFRESH_TOKEN`, and relevant scopes. Do not print tokens in logs.
4. Configure `VEBALIST_GOOGLE_CLIENT_ID` with the Google OAuth web client ID and `VEBALIST_ALLOWED_GOOGLE_EMAILS` with the comma-separated exact email allowlist. User tokens are verified server-side; if either setting is absent, Google sign-in fails closed.
5. Configure the same public web client ID as `VEBALIST_GOOGLE_CLIENT_ID` when building the Android app (Gradle property `vebalistGoogleClientId` is also supported). The client ID is not a secret; do not place private keys or eBay credentials in the app.
6. Keep the service private to trusted callers where practical. Web clients must use a same-origin server proxy that forwards the user's Google ID token. The backend verifies the token and allowlisted email; the proxy must not expose `VEBALIST_API_KEY` to the browser.
7. Existing unauthenticated clients receive HTTP 401. When no auth method is configured, protected endpoints return HTTP 503.
8. Check `GET /healthz` for container liveness. This endpoint does not check eBay OAuth; use authenticated `GET /api/status` for that.
9. Validate with a staging deployment and a test eBay account before changing production traffic. Publishing requires `confirm_publish: true` and an explicit user review and confirmation.

## Safety

Do not automatically merge this branch or publish eBay listings. Confirm CI and client compatibility first. Production Cloud Run and Replit are intentionally unchanged.
