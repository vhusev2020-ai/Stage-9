#!/usr/bin/env python3
"""Free local photo preparation and VEbalist photo-batch export."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import traceback
import urllib.request
import zipfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable

from PIL import Image, ImageEnhance, ImageFilter, ImageOps

IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".tif", ".tiff"}
OUTPUT_SIZE = 1600
JPEG_QUALITY = 94
BATCH_VERSION = 3
WORKFLOW_VERSION = "1.0"
MODEL_URL = "https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx"
MODEL_SHA256 = "309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8"


@dataclass(frozen=True)
class ProductGroup:
    name: str
    files: tuple[Path, ...]


def safe_name(value: str, fallback: str) -> str:
    cleaned = re.sub(r"[^A-Za-z0-9._ -]+", "", value).strip(" ._-")
    return cleaned or fallback


def discover_groups(source: Path) -> list[ProductGroup]:
    """Each immediate image-containing subfolder is a product; root images form one product."""
    source = source.expanduser().resolve()
    if not source.is_dir():
        raise ValueError("Choose a folder containing product photos.")

    root_files = tuple(sorted(p for p in source.iterdir() if p.is_file() and p.suffix.lower() in IMAGE_EXTENSIONS))
    groups: list[ProductGroup] = []
    if root_files:
        groups.append(ProductGroup(safe_name(source.name, "Product 1"), root_files))

    for child in sorted(p for p in source.iterdir() if p.is_dir() and not p.name.startswith(".")):
        files = tuple(sorted(p for p in child.rglob("*") if p.is_file() and p.suffix.lower() in IMAGE_EXTENSIONS))
        if files:
            groups.append(ProductGroup(safe_name(child.name, f"Product {len(groups) + 1}"), files))
    if not groups:
        raise ValueError("No supported photos were found. Use JPG, PNG, WEBP, HEIC, or TIFF files.")
    return groups


def cover_background(background: Image.Image, size: int) -> Image.Image:
    image = ImageOps.exif_transpose(background).convert("RGB")
    return ImageOps.fit(image, (size, size), method=Image.Resampling.LANCZOS, centering=(0.5, 0.5))


def neutral_background(mode: str, custom: Path | None, size: int) -> Image.Image:
    if mode == "white":
        return Image.new("RGB", (size, size), "#FFFFFF")
    if mode == "gray":
        return Image.new("RGB", (size, size), "#F2F3F5")
    if mode == "custom":
        if custom is None or not custom.is_file():
            raise ValueError("Choose a custom background image.")
        with Image.open(custom) as bg:
            return cover_background(bg, size).filter(ImageFilter.GaussianBlur(radius=1.2))
    raise ValueError(f"Unsupported background mode: {mode}")


def square_with_margins(image: Image.Image, size: int, color: str = "#F2F3F5") -> Image.Image:
    fitted = ImageOps.contain(image.convert("RGB"), (size, size), Image.Resampling.LANCZOS)
    canvas = Image.new("RGB", (size, size), color)
    canvas.paste(fitted, ((size - fitted.width) // 2, (size - fitted.height) // 2))
    return canvas


def normalize_subject(subject: Image.Image, size: int) -> Image.Image:
    rgba = ImageOps.exif_transpose(subject).convert("RGBA")
    alpha = rgba.getchannel("A")
    box = alpha.getbbox()
    if box:
        rgba = rgba.crop(box)
    max_subject = int(size * 0.9)
    rgba.thumbnail((max_subject, max_subject), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    x = (size - rgba.width) // 2
    y = (size - rgba.height) // 2
    canvas.alpha_composite(rgba, (x, y))
    return canvas


class LocalBackgroundRemover:
    def __init__(self) -> None:
        self._session = None

    @staticmethod
    def model_path() -> Path:
        cache = Path.home() / ".cache" / "vebalist-photo-prep"
        cache.mkdir(parents=True, exist_ok=True)
        model = cache / "u2netp.onnx"
        def valid(path: Path) -> bool:
            if not path.is_file() or path.stat().st_size < 1_000_000:
                return False
            digest = hashlib.sha256()
            with path.open("rb") as stream:
                for block in iter(lambda: stream.read(1024 * 1024), b""):
                    digest.update(block)
            return digest.hexdigest() == MODEL_SHA256

        if not valid(model):
            temporary = model.with_suffix(".download")
            urllib.request.urlretrieve(MODEL_URL, temporary)
            if not valid(temporary):
                temporary.unlink(missing_ok=True)
                raise RuntimeError("The background-removal model download failed its integrity check.")
            temporary.replace(model)
        return model

    def remove(self, image: Image.Image) -> Image.Image:
        try:
            import numpy as np
            import onnxruntime as ort
        except ImportError as exc:
            raise RuntimeError("The local background-removal component is not installed. Run the launcher again.") from exc
        if self._session is None:
            self._session = ort.InferenceSession(str(self.model_path()), providers=["CPUExecutionProvider"])

        original = ImageOps.exif_transpose(image).convert("RGB")
        sample = original.resize((320, 320), Image.Resampling.LANCZOS)
        array = np.asarray(sample, dtype=np.float32) / 255.0
        mean = np.array([0.485, 0.456, 0.406], dtype=np.float32)
        std = np.array([0.229, 0.224, 0.225], dtype=np.float32)
        array = (array - mean) / std
        tensor = np.transpose(array, (2, 0, 1))[None, ...]
        input_name = self._session.get_inputs()[0].name
        output = self._session.run(None, {input_name: tensor})[0]
        mask = np.squeeze(output).astype(np.float32)
        low, high = float(mask.min()), float(mask.max())
        if high > low:
            mask = (mask - low) / (high - low)
        mask_image = Image.fromarray((mask * 255).astype(np.uint8), mode="L")
        mask_image = mask_image.resize(original.size, Image.Resampling.LANCZOS)
        mask_image = mask_image.filter(ImageFilter.MedianFilter(size=3)).filter(ImageFilter.GaussianBlur(radius=0.6))
        rgba = original.convert("RGBA")
        rgba.putalpha(mask_image)
        return rgba


def prepare_image(
    source: Path,
    destination: Path,
    background_mode: str,
    custom_background: Path | None,
    remove_background: bool,
    remover: LocalBackgroundRemover | None,
) -> None:
    with Image.open(source) as opened:
        image = ImageOps.exif_transpose(opened).convert("RGB")
        if background_mode == "natural":
            # Preserve all real scene evidence. No segmentation, invented pixels, or selective blur.
            cleaned = ImageEnhance.Brightness(image).enhance(1.02)
            finished = square_with_margins(cleaned, OUTPUT_SIZE)
        elif remove_background:
            if remover is None:
                raise RuntimeError("Background remover was not initialized.")
            subject = normalize_subject(remover.remove(image), OUTPUT_SIZE)
            background = neutral_background(background_mode, custom_background, OUTPUT_SIZE).convert("RGBA")
            alpha = subject.getchannel("A")
            shadow_alpha = alpha.filter(ImageFilter.GaussianBlur(radius=14)).point(lambda value: int(value * 0.2))
            shadow = Image.new("RGBA", subject.size, (25, 25, 25, 0))
            shadow.putalpha(shadow_alpha)
            shadow_canvas = Image.new("RGBA", subject.size, (0, 0, 0, 0))
            shadow_canvas.alpha_composite(shadow, (0, 10))
            finished = Image.alpha_composite(Image.alpha_composite(background, shadow_canvas), subject).convert("RGB")
        else:
            # Preserve the image content while producing a consistent eBay-ready square.
            background = neutral_background(background_mode, custom_background, OUTPUT_SIZE)
            contained = ImageOps.contain(image, (int(OUTPUT_SIZE * 0.9), int(OUTPUT_SIZE * 0.9)), Image.Resampling.LANCZOS)
            finished = background.copy()
            finished.paste(contained, ((OUTPUT_SIZE - contained.width) // 2, (OUTPUT_SIZE - contained.height) // 2))

    finished = ImageEnhance.Contrast(finished).enhance(1.02)
    destination.parent.mkdir(parents=True, exist_ok=True)
    finished.save(destination, "JPEG", quality=JPEG_QUALITY, optimize=True, progressive=True)


def placeholder_listing(sku: str, photos: list[str]) -> dict:
    return {
        "sku": sku,
        "title": "",
        "description": "",
        "category_id": "",
        "condition": "",
        "condition_description": "",
        "price": None,
        "quantity": 1,
        "item_specifics": {},
        "shipping": {
            "weight_pounds": None,
            "weight_ounces": None,
            "package_length": None,
            "package_width": None,
            "package_height": None,
            "package_type": "",
            "fulfillment_policy_id": "",
        },
        "payment_policy_id": "",
        "return_policy_id": "",
        "inventory_location_key": "",
        "photos": photos,
        "market_research": {
            "checked_at": "",
            "comparable_price_low": None,
            "comparable_price_high": None,
            "sources": [],
            "notes": "Desktop photo preparation complete; ChatGPT research required.",
        },
        "policy_review": {
            "publish_allowed": False,
            "block_reason": "ChatGPT research and user approval required",
            "warnings": [],
        },
    }


def build_batch(
    source: Path,
    output_parent: Path,
    background_mode: str = "natural",
    custom_background: Path | None = None,
    remove_background: bool = True,
    progress: Callable[[str], None] | None = None,
) -> tuple[Path, Path]:
    notify = progress or (lambda _: None)
    groups = discover_groups(source)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    work = output_parent.expanduser().resolve() / f"VEbalist-Photos-{stamp}"
    work.mkdir(parents=True, exist_ok=False)
    remover = LocalBackgroundRemover() if remove_background and background_mode != "natural" else None
    batch_rows = []
    manifest_groups = []

    total = sum(len(group.files) for group in groups)
    completed = 0
    for group_index, group in enumerate(groups, 1):
        folder_name = f"listing_{group_index:03d}"
        folder = work / folder_name
        originals = folder / "originals"
        originals.mkdir(parents=True)
        edited_names: list[str] = []
        original_names: list[str] = []

        for photo_index, source_file in enumerate(group.files, 1):
            completed += 1
            notify(f"Product {group_index}/{len(groups)} • photo {completed}/{total}: {source_file.name}")
            original_name = f"original_{photo_index:03d}{source_file.suffix.lower()}"
            edited_name = f"photo_{photo_index:03d}.jpg"
            shutil.copy2(source_file, originals / original_name)
            prepare_image(
                source_file,
                folder / edited_name,
                background_mode,
                custom_background,
                remove_background,
                remover,
            )
            original_names.append(f"originals/{original_name}")
            edited_names.append(edited_name)

        sku = f"DESK-{group_index:03d}"
        (folder / "listing.json").write_text(
            json.dumps(placeholder_listing(sku, edited_names), indent=2), encoding="utf-8"
        )
        batch_rows.append({"folder": folder_name})
        manifest_groups.append({
            "folder": folder_name,
            "product_name": group.name,
            "sku": sku,
            "originals": original_names,
            "edited": edited_names,
        })

    (work / "batch.json").write_text(json.dumps({
        "batch_version": BATCH_VERSION,
        "workflow_version": WORKFLOW_VERSION,
        "package_type": "vebalist_desktop_photos",
        "marketplace": "EBAY_US",
        "currency": "USD",
        "listings": batch_rows,
    }, indent=2), encoding="utf-8")
    (work / "photo-prep-manifest.json").write_text(json.dumps({
        "created_at": datetime.now(timezone.utc).isoformat(),
        "app": "VEbalist Photo Prep",
        "workflow_version": WORKFLOW_VERSION,
        "background": background_mode,
        "background_removed": remove_background,
        "output_size": [OUTPUT_SIZE, OUTPUT_SIZE],
        "groups": manifest_groups,
        "note": "Originals are preserved for condition evidence. Edited photos are listing presentation copies.",
    }, indent=2), encoding="utf-8")

    archive = output_parent.expanduser().resolve() / f"{work.name}.zip"
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
        for path in sorted(work.rglob("*")):
            if path.is_file():
                zf.write(path, path.relative_to(work).as_posix())
    notify(f"Complete: {len(groups)} products and {total} photos")
    return work, archive


def reveal(path: Path) -> None:
    if sys.platform == "darwin":
        subprocess.run(["open", "-R", str(path)], check=False)
    elif os.name == "nt":
        subprocess.run(["explorer", "/select,", str(path)], check=False)
    else:
        subprocess.run(["xdg-open", str(path.parent)], check=False)


def run_gui() -> None:
    import tkinter as tk
    from tkinter import filedialog, messagebox, ttk

    root = tk.Tk()
    root.title("VEbalist Photo Prep")
    root.geometry("720x560")
    root.minsize(650, 520)

    source_var = tk.StringVar()
    output_var = tk.StringVar(value=str(Path.home() / "Desktop"))
    background_var = tk.StringVar(value="natural")
    custom_var = tk.StringVar()
    removal_var = tk.BooleanVar(value=True)
    status_var = tk.StringVar(value="Choose a folder. Put each product in its own subfolder.")

    frame = ttk.Frame(root, padding=24)
    frame.pack(fill="both", expand=True)
    ttk.Label(frame, text="VEbalist Photo Prep", font=("Helvetica", 26, "bold")).pack(anchor="w")
    ttk.Label(frame, text="Free local photo preparation • originals always preserved", foreground="#52606D").pack(anchor="w", pady=(2, 22))

    def path_row(label: str, variable: tk.StringVar, chooser: Callable[[], str]) -> None:
        ttk.Label(frame, text=label, font=("Helvetica", 13, "bold")).pack(anchor="w")
        row = ttk.Frame(frame)
        row.pack(fill="x", pady=(4, 14))
        ttk.Entry(row, textvariable=variable).pack(side="left", fill="x", expand=True)
        ttk.Button(row, text="Choose…", command=lambda: variable.set(chooser() or variable.get())).pack(side="left", padx=(8, 0))

    path_row("1. Product photo folder", source_var, lambda: filedialog.askdirectory(title="Choose product photo folder"))
    path_row("2. Save completed ZIP in", output_var, lambda: filedialog.askdirectory(title="Choose output folder"))

    ttk.Label(frame, text="3. Background", font=("Helvetica", 13, "bold")).pack(anchor="w")
    bg_row = ttk.Frame(frame)
    bg_row.pack(fill="x", pady=(4, 8))
    for text, value in (("Natural original", "natural"), ("Light gray", "gray"), ("White", "white"), ("My background", "custom")):
        ttk.Radiobutton(bg_row, text=text, value=value, variable=background_var).pack(side="left", padx=(0, 18))
    custom_row = ttk.Frame(frame)
    custom_row.pack(fill="x", pady=(0, 12))
    ttk.Entry(custom_row, textvariable=custom_var).pack(side="left", fill="x", expand=True)
    ttk.Button(custom_row, text="Choose background…", command=lambda: custom_var.set(
        filedialog.askopenfilename(title="Choose background image", filetypes=[("Images", "*.jpg *.jpeg *.png *.webp")]) or custom_var.get()
    )).pack(side="left", padx=(8, 0))

    ttk.Checkbutton(frame, text="Remove background for gray, white, or custom modes only", variable=removal_var).pack(anchor="w", pady=(4, 18))
    ttk.Label(frame, textvariable=status_var, wraplength=650, foreground="#0F766E").pack(anchor="w", pady=(0, 12))
    progress_bar = ttk.Progressbar(frame, mode="indeterminate")
    progress_bar.pack(fill="x", pady=(0, 14))

    run_button = ttk.Button(frame, text="Prepare photos and create VEbalist ZIP")
    run_button.pack(fill="x", ipady=12)
    ttk.Label(frame, text="First background-removal run downloads a small free model. Photos stay on this Mac.", foreground="#6B7280").pack(anchor="w", pady=(12, 0))

    def start() -> None:
        source = Path(source_var.get()).expanduser()
        output = Path(output_var.get()).expanduser()
        custom = Path(custom_var.get()).expanduser() if custom_var.get().strip() else None
        if background_var.get() == "custom" and (custom is None or not custom.is_file()):
            messagebox.showerror("Choose a background", "Select the background image you want to use.")
            return
        run_button.configure(state="disabled")
        progress_bar.start(10)

        def update(message: str) -> None:
            root.after(0, status_var.set, message)

        def worker() -> None:
            try:
                _, archive = build_batch(source, output, background_var.get(), custom, removal_var.get(), update)
                root.after(0, lambda: finished(archive))
            except Exception as exc:
                traceback.print_exc()
                root.after(0, lambda: failed(str(exc)))

        threading.Thread(target=worker, daemon=True).start()

    def finished(archive: Path) -> None:
        progress_bar.stop()
        run_button.configure(state="normal")
        status_var.set(f"Ready: {archive.name}")
        if messagebox.askyesno("VEbalist ZIP ready", f"Saved:\n{archive}\n\nShow it in Finder?"):
            reveal(archive)

    def failed(message: str) -> None:
        progress_bar.stop()
        run_button.configure(state="normal")
        status_var.set("Photo preparation stopped. Nothing was overwritten.")
        messagebox.showerror("Unable to prepare photos", message)

    run_button.configure(command=start)
    root.mainloop()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cli", action="store_true", help="Run without the desktop window")
    parser.add_argument("source", nargs="?")
    parser.add_argument("output", nargs="?")
    parser.add_argument("--background", choices=("natural", "gray", "white", "custom"), default="natural")
    parser.add_argument("--custom-background")
    parser.add_argument("--no-removal", action="store_true")
    args = parser.parse_args()
    if not args.cli:
        run_gui()
        return 0
    if not args.source or not args.output:
        parser.error("CLI mode requires source and output folders")
    _, archive = build_batch(
        Path(args.source),
        Path(args.output),
        args.background,
        Path(args.custom_background) if args.custom_background else None,
        not args.no_removal,
        print,
    )
    print(archive)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
