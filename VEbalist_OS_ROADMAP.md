# VEbalist — Free-First Reseller Operating System

## Product direction
VEbalist is intended to grow from a batch eBay listing helper into a practical resale-business operating system. The first user workflow is simple: receive an item, photograph and prepare it, record it once, research it, approve a listing, then track the item through sale and fulfillment.

Build for Android first, with a local-first data model so the core inventory workflow can be used without paying for a hosted backend. Add optional sync and marketplace integrations only when needed.

## Design principles
1. Free first: prefer Android platform features, open-source libraries, GitHub's free tier, and existing Google tools. Do not introduce a paid subscription or cloud service without a demonstrated need.
2. Local-first: inventory records and drafts should be usable on the phone without an internet connection. Cloud backup/sync is optional, not a requirement for basic use.
3. One source of truth: each physical item gets one SKU and one record, even when prepared for multiple marketplaces.
4. Human approval: AI may suggest titles, categories, item specifics, prices, and descriptions. A person reviews and approves every listing.
5. No accidental publishing: all marketplace publishing stays disabled until the account owner separately approves a specific live workflow. No bulk publish in the early versions.
6. Portable data: export and backup should use ordinary files (CSV/JSON and photo folders) so the business is not locked into VEbalist.
7. Privacy and security: never put marketplace passwords, OAuth refresh tokens, or backend secrets in the app source, APK, inventory export, or chat.

## Core modules
### 1. Home / business dashboard
- Items needing preparation, ready for research, ready for review, listed, sold, and awaiting shipment.
- Simple sales, costs, and estimated/actual profit totals.
- Clear empty states and next actions; avoid complex accounting claims.

### 2. Inventory
- Unique SKU, item name, category, brand, condition, quantity, storage location, date acquired, source, purchase cost, and notes.
- Multiple photos per item.
- Status lifecycle: Received → Cleaning → Photography → Research → Draft → Approved → Listed → Sold → Shipped; support Returned and Archived.
- Search and filters by SKU, title, status, category, and location.
- Manual entry and batch import.

### 3. Photo / preparation station
- Capture or attach photos from Android.
- Photo checklist: front, back, label/model, size/tag, flaws, accessories, packaging when useful.
- Keep original photos linked to the item; do not overwrite originals during edits.
- Record cleaning/repair tasks and completion.

### 4. Research and listing assistant
- AI-generated draft title, description, category suggestions, item specifics, and condition notes.
- Price research must distinguish asking prices from completed/sold prices and show source/date when available.
- Show uncertainty and missing facts; never invent model numbers, materials, dimensions, or condition details.
- Drafts are editable and never publish automatically.

### 5. Marketplace listing manager
- First integration target: eBay, using the existing backend and validation flow.
- Prepare platform-specific drafts from the shared inventory record.
- Track each marketplace listing separately while retaining one physical-item record.
- Later: Facebook Marketplace, Mercari, Depop, and Vinted, subject to each platform's permitted APIs and terms.
- No scraping, bot automation, or unsupported integrations that risk account restrictions.

### 6. Orders, shipping, and returns
- Record order date, marketplace, sale price, fees, shipping cost, refunds, tracking, shipment status, and returns.
- Calculate net proceeds and actual item-level profit from recorded amounts.
- Do not claim tax/accounting compliance; export records for the user's accountant or chosen tool.

### 7. Reports and backups
- CSV export for inventory and sales; JSON backup for full records.
- Photo export or folder backup.
- Restore test before relying on backups.
- Optional Google Drive backup can be evaluated after local storage works; no account credentials embedded in app.

## Suggested data model
- Item: id, SKU, title, description, category, brand, condition, condition notes, quantity, location, source, acquired date, purchase cost, currency, status, notes, created/updated timestamps.
- Photo: id, item id, local URI/path, photo type, sort order, created timestamp.
- ListingDraft: id, item id, marketplace, title, description, category, item specifics, asking price, shipping details, policy references, review status.
- MarketplaceListing: id, item id, marketplace, external listing id, listing URL, status, listed date, last checked date.
- Order: id, item id, marketplace, order reference, sold date, sale price, fees, shipping cost, tracking, fulfillment status.
- Expense: id, optional item/order id, type, amount, date, note.
- Activity: id, item id, event type, timestamp, note; used for an understandable item history.
Store money as integer cents, not floating-point currency. Keep IDs stable and avoid using a title as an identifier.

## Delivery plan
### Phase 0 — Safe build and install
- Keep the existing safety-hardening work on its review branch.
- Get the Android debug build green and installable.
- Verify the app opens on a real Android phone.
- Keep eBay publishing disabled and do not connect live credentials for this milestone.

### Phase 1 — Offline inventory MVP
- Add, edit, search, and archive an item locally.
- Capture/attach multiple photos.
- Assign SKU and storage location.
- Track preparation status.
- Persist records across app restarts.
- Export a backup and restore it in a test.
Acceptance test: create five sample items, close/reopen the app, search them, edit one, attach photos, export a backup, and restore the backup without losing records.

### Phase 2 — Listing preparation
- Generate/import a draft from an inventory item.
- Add price, shipping, condition, category, and item specifics.
- Validate required fields.
- Show a final human-readable review screen.
- Export a marketplace-neutral listing packet.

### Phase 3 — eBay staging integration
- Connect only an account explicitly authorized by its owner.
- Keep credentials server-side.
- Read existing seller policies and locations.
- Validate a single draft without publishing.
- Test with the account owner's approval and a documented rollback plan.

### Phase 4 — Orders and profitability
- Record sold items, fees, postage, refunds, and actual profit.
- Add order and shipment workflow.
- Provide CSV exports and basic reports.

### Phase 5 — Optional sync and additional marketplaces
- Evaluate free Google Drive backup/sync or another low-cost option only after local-first behavior is reliable.
- Add other marketplaces only through supported, permitted integrations.
- Keep each integration optional and independently disableable.

## Current implementation reality
The current Android app is an eBay batch-import and listing-validation prototype. It is not yet a full Reseller OS: it does not yet provide the offline inventory database, item photo station, complete order/expense ledger, or multi-marketplace management described above.

The existing safety branch adds an API-key gate, keeps publishing disabled by default, limits a publish request to one item, makes account setup read-only for inventory locations, and adds a CI workflow intended to build a debug APK. These safeguards must remain in place while the product grows.

## Free-resource policy
- Android app: Kotlin and Android SDK.
- Source and issue tracking: existing GitHub repository/free tier.
- Automated builds: GitHub Actions free allowance, subject to account limits.
- Local storage: Android app-private storage initially; choose a supported local database when implementing Phase 1.
- Backups: ordinary export files first; consider existing Google Drive space later.
- AI: start with manual/ChatGPT-assisted draft preparation and free tiers where available. Do not make the core app unusable when an AI quota is exhausted.
- Hosting: no hosted backend is required for Phase 1. Keep the current backend optional for later eBay staging work.

## Out of scope for the first installable version
- Live marketplace publishing.
- Automatic cross-listing.
- Paid AI or hosting subscriptions.
- Automatic repricing or purchasing.
- Accounting/tax filing.
- Multi-user roles and remote team collaboration.

## Next engineering milestone
Build Phase 0 reliably, then implement Phase 1 as a separate, testable change. Do not merge this review branch into main, deploy it, configure live eBay credentials, or enable publishing without explicit approval.