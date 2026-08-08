# VEbalist 2.0.1

VEbalist is a native Kotlin Android frontend with a Flask backend for validating
and publishing listing batches through the eBay Inventory API.

## Intended listing workflow

1. Upload item pictures and provide known details in ChatGPT.
2. ChatGPT creates a VEbalist batch ZIP containing the pictures and structured
   listing data.
3. Import that ZIP with `Import ChatGPT Listing Batch` in the Android app.
4. VEbalist lets you review/correct the listing, loads category-required eBay
   fields, validates it, and publishes it through your authenticated backend.

The Android interface presents this as four guided sections: Connect, Import,
Review, and Publish. `Export Error Log` saves a shareable text report containing
validation and publishing failures while intentionally excluding service keys
and eBay credentials.

ChatGPT prepares the portable file; only VEbalist is authorized to perform the
eBay validation and publishing actions. The app can also create a listing
directly or export a corrected batch for reuse. Each listing
supports multiple pictures, SKU, title, description, category, condition and
condition notes, price, quantity, category-specific item details, payment/return/
fulfillment policies, inventory location, packed weight, package dimensions, and
an optional eBay package type. Required category details are loaded from eBay and
validated before publication.

`Save Listing Batch ZIP` creates the complete portable batch locally on the
Android device. Creating or editing this batch does not call an AI service or the
VEbalist backend, so it does not incur generation API expense. Network access is
only used when the user explicitly loads eBay setup/category data, validates, or
publishes.

## Project layout

- `app/` — Android application (`com.vcorp.vebalist`)
- `backend/` — Flask/eBay API deployed to Google Cloud Run
- `sample_batch/` — example ZIP batch contract
- `scripts/` — local setup, checks, and release-keystore helper

## Local workflow

Requirements: Java 17, Gradle 8.7, and Python 3.12.

```sh
./scripts/setup.sh
./scripts/check.sh
```

Run the backend:

```sh
backend/.venv/bin/gunicorn --chdir backend --bind 0.0.0.0:8080 app:app
```

Build the Android frontend with a default backend URL:

```sh
VEBALIST_BACKEND_URL=https://your-cloud-run-service.run.app \
  gradle --no-daemon assembleDebug
```

The backend address remains editable in the app. The build-time value is only
the initial default for clean installations. Enter the `VEBALIST_API_KEY`
value from Secret Manager in the app before testing the backend.

## Automation

- `CI` validates backend tests, the container, Android lint, and APK build.
- `Build VEbalist APK` publishes a downloadable debug APK artifact.
- `Deploy backend to Cloud Run` builds, deploys, and health-checks the backend.
- `backend/cloudbuild.yaml` is the Google Cloud Build alternative.

See [CLOUD_RUN_DEPLOYMENT.md](CLOUD_RUN_DEPLOYMENT.md) for the required Google
Cloud and GitHub configuration.

Never store eBay credentials, refresh tokens, signing keystores, or passwords
in the repository or APK.
