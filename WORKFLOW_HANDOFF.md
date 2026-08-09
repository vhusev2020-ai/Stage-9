# VEbalist fixed workflow handoff

## Current release

- App: VEbalist 2.0.5 (205)
- Workflow: 1.0
- Android build: verified with `assembleDebug`
- Emulator: installed and launched on Android API 36
- Primary action: **Create and publish listings**

## Fixed sequence

1. Import the desktop-prepared product ZIP.
2. ChatGPT assesses visible condition from the complete photo set and researches the remaining listing details and shipping estimate.
3. Export and share the research ZIP to the signed-in ChatGPT app.
4. Import the completed `VEbalist-Ready-<batch-id>.zip`.
5. Validate required eBay fields and policy decisions.
6. Require final user approval.
7. Publish through the existing Google Cloud backend.
8. Record failures in the workflow improvement log and resume at the failed stage.

ChatGPT subscription usage is an Android share/file handoff. It is not an embedded API and must not store ChatGPT credentials, cookies, or session data.

## Implemented

- Persistent workflow stage and batch ID
- Fixed workflow version
- One primary resume button
- Research-package ZIP with embedded best-selling, pricing, shipping, and policy instructions
- Android share sheet handoff to ChatGPT
- File association for returned ZIP packages
- Publication approval checkpoint
- Failure-to-improvement-log recording
- Workflow metadata and recent issues in exported diagnostics
- FileProvider-based secure temporary sharing
- Free Mac photo-preparation tool with product-folder grouping, preserved originals, natural-original default, optional local U²-Net background removal, and VEbalist ZIP export
- ChatGPT photographic condition assessment with confidence and mandatory warnings for facts photographs cannot establish

## Safety rules

- Never publish without user approval.
- Never silently adopt an error resolution as a permanent rule.
- Never expose backend keys, eBay credentials, ChatGPT credentials, or cookies in exported files.
- Shipping estimates require confirmation when confidence is not high.
- Condition is decided from photographs, with visible evidence and confidence recorded. Low-confidence, functional, hidden, completeness, and authenticity questions require user review.
- Preserve original product photographs as condition evidence.

## Verification

The project compiled successfully, and the API 36 emulator installed and launched the APK. The primary workflow button correctly opened the Android ZIP picker. No VEbalist fatal exception was present in logcat. The emulator showed a transient System UI low-memory warning due to constrained host memory; this was unrelated to VEbalist.

## Next implementation increment

1. Add an in-app improvement-log screen with explicit approve/reject controls.
2. Add automated UI tests for resume, share, returned-ZIP import, and final approval.
3. Add strict returned-package batch-ID validation.
4. Test the complete handoff using the installed ChatGPT Android app on a physical phone.
