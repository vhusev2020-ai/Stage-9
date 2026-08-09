import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from PIL import Image

from photo_prep import build_batch, discover_groups


class PhotoPrepTests(unittest.TestCase):
    def test_discovers_product_subfolders_and_exports_batch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "products"
            output = root / "out"
            output.mkdir()
            for product in ("Blue Shirt", "Coffee Mug"):
                folder = source / product
                folder.mkdir(parents=True)
                Image.new("RGB", (800, 600), "navy").save(folder / "one.jpg")

            self.assertEqual([g.name for g in discover_groups(source)], ["Blue Shirt", "Coffee Mug"])
            work, archive = build_batch(source, output, background_mode="natural", remove_background=False)

            self.assertTrue(archive.is_file())
            batch = json.loads((work / "batch.json").read_text())
            self.assertEqual(batch["package_type"], "vebalist_desktop_photos")
            self.assertEqual(len(batch["listings"]), 2)
            with zipfile.ZipFile(archive) as zf:
                names = set(zf.namelist())
                self.assertIn("listing_001/photo_001.jpg", names)
                self.assertIn("listing_001/originals/original_001.jpg", names)
                self.assertIn("listing_002/listing.json", names)


if __name__ == "__main__":
    unittest.main()
