#!/usr/bin/env python3
"""Regroup an already-edited flat VEbalist batch without altering its photos."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import shutil
import zipfile
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

from photo_prep import BATCH_VERSION, WORKFLOW_VERSION, placeholder_listing


def digest(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def load_group_map(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle, delimiter="\t"))
    required = {"group", "photo", "original_file"}
    if not rows or not required.issubset(rows[0]):
        raise ValueError("The group map must contain group, photo, and original_file columns.")
    return rows


def regroup(
    prepared_batch: Path,
    group_map: Path,
    reference_groups: Path,
    output_parent: Path,
    limit: int | None = None,
) -> tuple[Path, Path]:
    source_listing = prepared_batch / "listing_001"
    originals = sorted((source_listing / "originals").glob("original_*"))
    edited = sorted(source_listing.glob("photo_*.jpg"))
    if not originals or len(originals) != len(edited):
        raise ValueError("The flat batch must have one edited photo for every preserved original.")

    edited_by_hash: dict[str, Path] = {}
    for original, finished in zip(originals, edited, strict=True):
        key = digest(original)
        if key in edited_by_hash:
            raise ValueError(f"Duplicate original photo detected: {original.name}")
        edited_by_hash[key] = finished

    rows = load_group_map(group_map)
    grouped: dict[str, list[tuple[str, Path]]] = defaultdict(list)
    for row in rows:
        reference = reference_groups / row["group"] / row["photo"]
        if not reference.is_file():
            raise FileNotFoundError(f"Missing grouping reference: {reference}")
        finished = edited_by_hash.get(digest(reference))
        if finished is None:
            raise ValueError(f"No edited match for {row['original_file']}")
        grouped[row["group"]].append((row["photo"], finished))

    ordered_groups = sorted(grouped)
    if limit is not None:
        ordered_groups = ordered_groups[:limit]

    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    suffix = f"-First-{limit}" if limit else ""
    work = output_parent.resolve() / f"VEbalist-Grouped-Edited{suffix}-{stamp}"
    work.mkdir(parents=True, exist_ok=False)

    batch_rows = []
    manifest_rows = []
    for index, group_name in enumerate(ordered_groups, 1):
        listing_folder = f"listing_{index:03d}"
        destination = work / listing_folder
        destination.mkdir()
        photos = []
        for photo_index, (_, finished) in enumerate(grouped[group_name], 1):
            name = f"photo_{photo_index:03d}.jpg"
            shutil.copy2(finished, destination / name)
            photos.append(name)

        listing = placeholder_listing(f"DESK-{index:03d}", photos)
        listing["title"] = group_name.split("_", 1)[1].replace("_", " ").title()
        listing["market_research"]["notes"] = (
            "Desktop photo editing complete; photos grouped by product. "
            "ChatGPT research, condition assessment, pricing, and user approval required."
        )
        (destination / "listing.json").write_text(
            json.dumps(listing, indent=2), encoding="utf-8"
        )
        batch_rows.append({"folder": listing_folder})
        manifest_rows.append(
            {
                "folder": listing_folder,
                "product_name": listing["title"],
                "source_group": group_name,
                "photos": photos,
            }
        )

    batch = {
        "batch_version": BATCH_VERSION,
        "workflow_version": WORKFLOW_VERSION,
        "package_type": "vebalist_desktop_photos",
        "marketplace": "EBAY_US",
        "currency": "USD",
        "listings": batch_rows,
    }
    (work / "batch.json").write_text(json.dumps(batch, indent=2), encoding="utf-8")
    manifest = {
        "created_at": datetime.now(timezone.utc).isoformat(),
        "app": "VEbalist Photo Prep",
        "workflow_version": WORKFLOW_VERSION,
        "operation": "regroup_existing_edits",
        "source_batch": str(prepared_batch.resolve()),
        "photos_modified": False,
        "groups": manifest_rows,
    }
    (work / "photo-prep-manifest.json").write_text(
        json.dumps(manifest, indent=2), encoding="utf-8"
    )

    archive = output_parent.resolve() / f"{work.name}.zip"
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
        for path in sorted(work.rglob("*")):
            if path.is_file():
                zf.write(path, path.relative_to(work).as_posix())
    return work, archive


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("prepared_batch", type=Path)
    parser.add_argument("group_map", type=Path)
    parser.add_argument("reference_groups", type=Path)
    parser.add_argument("output_parent", type=Path)
    parser.add_argument("--limit", type=int)
    args = parser.parse_args()
    work, archive = regroup(
        args.prepared_batch,
        args.group_map,
        args.reference_groups,
        args.output_parent,
        args.limit,
    )
    print(json.dumps({"folder": str(work), "zip": str(archive)}, indent=2))


if __name__ == "__main__":
    main()
