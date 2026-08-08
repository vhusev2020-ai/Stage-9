# VEbalist automation summary

## Product workflow

ChatGPT packages uploaded item pictures and listing details into a batch ZIP.
VEbalist imports the ZIP, allows corrections, validates category requirements,
and publishes through the authenticated eBay backend. eBay credentials never
need to be provided to ChatGPT or stored in a generated batch.

## Listing data carried end to end

- Multiple pictures
- SKU, title, description, category, condition, and condition description
- Price and quantity
- Category-specific item details (eBay aspects)
- Packed weight in pounds and ounces
- Package length, width, height, and optional package type
- Payment, return, and fulfillment policies
- Inventory location

## Development automation

- Reliable Java/Python dependency setup and one-command checks
- Android lint and APK build in GitHub Actions
- Backend unit tests and container build in GitHub Actions
- Downloadable APK workflow artifact
- Google Cloud Run container build, deploy, secrets attachment, and health check
- Backend API-key protection between VEbalist and Cloud Run
- Docker and Cloud Build configuration
- Safe sample batch and deployment documentation

## Secrets

eBay OAuth credentials and the VEbalist backend key belong in Google Secret
Manager and GitHub configuration. They are excluded from source, batch ZIPs,
and APK source control.
