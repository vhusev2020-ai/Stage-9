# VEbalist Android — Stage 9 / Safety-hardening branch

This branch is a safety review of Stage 9. The original Stage 9 branch remains unchanged.

## Safety behavior

- All `/api/*` routes require the `X-VEbalist-Key` request header.
- `/healthz` is a minimal public health endpoint and does not expose eBay account data.
- The backend refuses protected API actions if `VEBALIST_API_KEY` is missing.
- Publishing is disabled by default. `VEBALIST_ALLOW_PUBLISH` must be explicitly enabled on the backend before the publish endpoint will proceed.
- Even when publishing is enabled, the backend accepts at most one listing per publish request.
- Account setup only reads existing eBay policies and inventory locations. It no longer creates a location automatically.
- eBay client secrets and refresh tokens remain server-side; they do not belong in the Android app.
- The Android app asks for the separate backend access key. It is not saved by the app.

## Configure safely

1. Deploy this branch to a separate preview/staging backend, not the current production service.
2. Add a long random value as `VEBALIST_API_KEY` in the hosting provider's secret/environment settings.
3. Set `VEBALIST_ALLOW_PUBLISH=false`.
4. Keep all `EBAY_*` credentials out of GitHub files and the APK. Enter them only in the hosting provider's secret settings when the account owner approves.
5. Build and install the Android debug APK from this branch.
6. Enter the HTTPS staging backend URL and the backend access key in the app.
7. Confirm `/healthz` responds, while app API calls require the key.
8. Load account setup and confirm that the app only reads existing policies/locations.
9. Import one listing and use **Test First Ready Listing**. This validation call does not publish.
10. Do not enable publishing until the code review, tests, account authorization, and one-item confirmation are complete.

## Important limitations

The backend access key is a practical first access gate, not a full user identity system. Anyone who can extract the key from a device where it is entered may be able to reuse it. Use a unique staging key, restrict access to the staging service where possible, and rotate the key if the device is lost or access changes. A stronger multi-user identity gateway should be considered before broad release.

The `VEBALIST_ALLOW_PUBLISH` switch is a server-side safety gate, not an eBay sandbox. If real eBay OAuth credentials are configured, read-only account and validation endpoints can still contact the live eBay account. No live listing should be published during the current safety-review stage.
