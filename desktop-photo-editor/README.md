# VEbalist Photo Prep

This free Mac desktop tool prepares photographs only. Listing research, details, validation, and eBay publishing remain in the VEbalist Android app.

## Use

1. Make one folder for the batch.
2. Inside it, make one subfolder per product and place that product's photos there.
3. Double-click `launch.command`.
4. Select the batch folder and an output location.
5. Choose **Natural original** (recommended), light gray, white, or your own background.
6. Press **Prepare photos and create VEbalist ZIP**.
7. Import the resulting ZIP into VEbalist.

The first use installs free local components and downloads the small `u2netp` ONNX background-removal model. It does not use Gemini, OpenAI API, or a paid photo service.

Every export contains untouched originals, 1600 × 1600 edited JPG files, a photo manifest, placeholder listing files, and a VEbalist-compatible `batch.json`.

Natural original keeps the entire real scene and only corrects orientation, framing, size, and very slight lighting. It does not selectively blur, generate, remove, or replace room details, so it will not have the artificial cutout appearance.

If a cutout damages an important label or product edge, turn background removal off for that batch or use the preserved original as an additional eBay photo.
