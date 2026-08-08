# Google Cloud Run deployment

## Required Google Cloud resources

1. Enable Cloud Run, Artifact Registry, Secret Manager, Cloud Build, and IAM
   Credentials APIs.
2. Create a Docker Artifact Registry repository named `vebalist` in the
   deployment region.
3. Store these Secret Manager secrets:
   - `EBAY_CLIENT_ID`
   - `EBAY_CLIENT_SECRET`
   - `EBAY_REFRESH_TOKEN`
   - `EBAY_REFRESH_SCOPES`
   - `VEBALIST_API_KEY` (generate a long random value and enter the same
     value in the app; do not compile it into the APK)
4. Create a deployment service account and Workload Identity Federation
   provider for GitHub Actions.
5. Grant only the roles needed to push the image, deploy Cloud Run, use the
   runtime service account, and attach the named secrets.

## GitHub configuration

Create a protected `production` environment with:

Secrets:

- `GCP_PROJECT_ID`
- `GCP_WORKLOAD_IDENTITY_PROVIDER`
- `GCP_SERVICE_ACCOUNT`

Optional repository variables:

- `GCP_REGION` (default `us-east1`)
- `CLOUD_RUN_SERVICE` (default `vebalist-backend`)
- `ARTIFACT_REPOSITORY` (default `vebalist`)
- `VEBALIST_BACKEND_URL` (the deployed Cloud Run HTTPS URL embedded as the
  Android app's initial backend)

Pushes affecting `backend/` deploy automatically. APK builds use
`VEBALIST_BACKEND_URL`; the URL can also be changed from inside VEbalist.

The unauthenticated Cloud Run URL is required because the Android client does
not currently send Cloud Run identity tokens. All application API routes are
still protected by `X-VEbalist-Key`; only `/healthz` is public. eBay
credentials remain server side in Secret Manager.
