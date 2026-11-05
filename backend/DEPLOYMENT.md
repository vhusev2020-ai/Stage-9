# VEbalist backend — deployment checklist (development branch)

This directory can be built as a container using `backend/Dockerfile` (set the build context to `backend`). No production deployment is performed by this repository change.

## Before deployment

1. Confirm backend unit tests pass: `python -m unittest discover -p 'test_*.py' -v` from `backend`.
2. Configure `VEBALIST_API_KEY` as a secret supplied at runtime, not in source code, browser JavaScript, mobile APK, build arguments, or container image.
3. Supply eBay OAuth credentials through runtime secret references: `EBAY_CLIENT_ID`, `EBAY_CLIENT_SECRET`, `EBAY_REFRESH_TOKEN`, and relevant scopes. Do not print tokens in logs.
4. Keep the service private to trusted callers where practical. A browser must use an authenticated server-side proxy; it must not hold the shared backend API key.
5. Update Android and web clients to authenticate before enabling this version; existing unauthenticated clients will receive HTTP 401. If the server key is missing, protected endpoints return 503.
6. Validate with a staging deployment and a test eBay account before changing production traffic. Publishing requires `confirm_publish: true` and must only occur after explicit user approval.
7. Check `GET /healthz` for container liveness. This endpoint does not check eBay OAuth; use authenticated `GET /api/status` for that.

## Safety

Do not automatically merge this branch or publish eBay listings. Confirm CI and client compatibility first. Production Cloud Run and Replit are intentionally unchanged.
