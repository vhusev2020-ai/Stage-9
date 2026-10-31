# VEbalist Production Checklist

## Safety review — staging only
- [ ] Deploy this branch to a separate staging service; do not replace production.
- [ ] Configure a unique VEBALIST_API_KEY in the host's secret settings.
- [ ] Keep VEBALIST_ALLOW_PUBLISH=false.
- [ ] Confirm unauthenticated /api requests return 401.
- [ ] Confirm /healthz returns only basic service health.
- [ ] Confirm account setup does not create or modify an eBay inventory location.
- [ ] Confirm no eBay secrets are present in GitHub or the APK.
- [ ] Run backend automated tests.
- [ ] Build Android debug APK and install on a test device.
- [ ] Confirm app requests fail without the backend key and succeed with it.
- [ ] Import one test item and run Test First Ready Listing.
- [ ] Verify no listing, offer, inventory item, or photo is created by validation.

## eBay account connection
- [ ] Account owner authorizes the connection.
- [ ] eBay OAuth credentials are entered only in hosting-provider secret settings.
- [ ] Confirm /api/status reports OAuth ready with the authorized account.
- [ ] Confirm existing payment, return, fulfillment policies and inventory locations are read-only.

## Live publishing — separate approval required
- [ ] Review code and automated tests.
- [ ] Confirm exact item, price, quantity, condition, shipping, photos, and policies.
- [ ] Account owner explicitly approves enabling VEBALIST_ALLOW_PUBLISH=true.
- [ ] Enable publishing only for a single-item supervised test.
- [ ] Verify returned listing ID and inspect the live eBay listing.
- [ ] Turn publishing back off after the test.

## Release
- [ ] Generate Android release keystore.
- [ ] Store signing secrets securely; never commit them.
- [ ] Build and install signed release APK.
- [ ] Back up release keystore securely.
- [ ] Complete a privacy and access-control review before multi-user release.
