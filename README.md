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
2. Build/install debug APK.
3. Deploy `backend/` over HTTPS.
4. Configure eBay OAuth secrets on the backend host.
5. Enter backend URL in APK.
6. Load eBay setup.
7. Import one real listing.
8. Run "Test First Ready Listing."
9. Publish that listing.
10. Configure release signing and build signed APK.

No eBay Client Secret or refresh token belongs inside the APK.
