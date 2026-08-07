# VEbalist Production Checklist

## Android
- [ ] Repository uploaded to GitHub
- [ ] GitHub Actions debug APK builds successfully
- [ ] APK installs on Android phone
- [ ] Batch ZIP imports correctly
- [ ] Required-field editor works
- [ ] Backend URL test succeeds
- [ ] eBay policies and inventory location load

## Backend
- [ ] Backend deployed over HTTPS
- [ ] EBAY_CLIENT_ID configured
- [ ] EBAY_CLIENT_SECRET configured
- [ ] EBAY_REFRESH_TOKEN configured
- [ ] EBAY_REFRESH_SCOPES configured
- [ ] /api/status returns OAuth ready

## Live test
- [ ] Import exactly one real listing
- [ ] Apply eBay account defaults
- [ ] Validate eBay fields
- [ ] Use Test First Ready Listing
- [ ] Publish the one listing
- [ ] Confirm returned listing ID appears on eBay
- [ ] Verify photos, title, description, item specifics, price and shipping

## Release
- [ ] Generate Android release keystore
- [ ] Add GitHub signing secrets
- [ ] GitHub builds signed release APK
- [ ] Install signed APK
- [ ] Keep release keystore backed up securely
