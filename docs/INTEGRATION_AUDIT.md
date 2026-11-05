# VEbalist integration audit

**Branch:** `security-and-tests-2026-11-05`  
**Reviewed:** 2026-11-05  
**Scope:** Backend CI and Android/web compatibility; no deployment or eBay publishing.

## Evidence

- GitHub Actions run `42014833680` (“Backend smoke tests”) completed successfully for branch head `a15d93b08a86ac196ecdbcc36f593d390627e9b4`.
- The repository contains Android app sources and Flask backend sources. No web application source or web build/deployment configuration is present in this branch.
- Android `BackendClient` does not send `X-API-Key`, but backend middleware requires it for `/api/*`. The Android app therefore cannot call protected backend endpoints.
- Android's publish request also omitted `confirm_publish`; the backend rejects publishing without it. A confirmation dialog is now required before Android sends the explicit confirmation flag.
- Android previously dropped packed weight and package dimensions from imported batches and omitted those values from validation/publish requests. Import mapping and request serialization now retain both the current backend field names and the sample batch's legacy names. Listings without complete packed shipping measurements are kept out of the ready state.
- The shared backend API key must not be embedded in an APK or browser bundle. No client-side shared-key workaround was added. A user-authenticated server-side gateway or equivalent identity flow is still needed before Android can connect safely.

## Checks

- GitHub Actions backend smoke-test workflow: passed on the current branch head (run above).
- Local backend unit tests: passed, 11 tests (`python -m unittest discover -p 'test_*.py' -v`).
- Android APK build: passed in GitHub Actions run `42015744611` on commit `1db77f8ddac2d00414ab6f0d08347a622eab4bdd`; the debug APK artifact uploaded successfully. Gradle was not installed locally.
- No live eBay API publishing or production deployment was performed.

## Remaining blockers

1. Choose and implement a safe Android authentication flow that does not ship the shared backend key.
2. Locate the web app and verify it uses a server-side authenticated proxy; current branch does not contain its source.
3. Extend the listing editor so users can modify packed weight and dimensions after import; current integration change imports and validates the values but does not yet add editor controls.
4. Build the Android APK in GitHub Actions and review the result before staging integration.
