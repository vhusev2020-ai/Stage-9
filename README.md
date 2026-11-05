# VEbalist Android — Stage 9 / Pre-Release

Stage 9 adds the final pre-release pieces:

- Backend deployment definition for Render.
- Gunicorn production server.
- Live OAuth/account readiness endpoint.
- One-listing validation endpoint.
- Android "Test First Ready Listing" action.
- Server checks the listing payload and exact eBay category-required item specifics before publishing.
- Sample ChatGPT batch contract version 2.
- Production checklist.
- Existing batch publish, retry, history, policy loading, location loading, aspect validation, and release-signing setup retained.

The final production sequence is now:
1. Upload project to GitHub.
2. Configure a Google OAuth web client and the backend email allowlist.
3. Build/install debug APK with the public Google client ID.
4. Deploy `backend/` over HTTPS.
5. Configure eBay OAuth secrets on the backend host.
6. Enter the backend URL in the APK and sign in with an allowlisted Google account.
7. Load eBay setup.
8. Import one real listing.
9. Run "Test First Ready Listing."
10. Review the listing and confirm publishing.
11. Configure release signing and build the signed APK.

No eBay Client Secret or refresh token belongs inside the APK.

## Development authentication

Android signs in with Google and keeps the resulting ID token in memory only. The backend verifies its signature, issuer, audience, verified email, and membership in `VEBALIST_ALLOWED_GOOGLE_EMAILS`. Build Android with the public Google OAuth web client ID supplied through `VEBALIST_GOOGLE_CLIENT_ID` or the Gradle property `vebalistGoogleClientId`.

The backend still accepts `X-API-Key` for trusted server-to-server callers. Never ship that shared key to Android or a browser. Configure `VEBALIST_GOOGLE_CLIENT_ID` and `VEBALIST_ALLOWED_GOOGLE_EMAILS` at the backend before staging. See [backend/DEPLOYMENT.md](backend/DEPLOYMENT.md).
