"""Authentication smoke tests. No eBay network calls or publishing."""
import os
import unittest
from unittest.mock import patch
from app import app

class AuthTests(unittest.TestCase):
    def setUp(self):
        self.client = app.test_client()

    def test_public_health(self):
        with patch.dict(os.environ, {}, clear=True):
            self.assertEqual(self.client.get("/healthz").status_code, 200)

    def test_missing_server_key_fails_closed(self):
        with patch.dict(os.environ, {}, clear=True):
            self.assertEqual(self.client.get("/api/status").status_code, 503)

    def test_missing_client_key_rejected(self):
        with patch.dict(os.environ, {"VEBALIST_API_KEY": "test-only-key"}):
            self.assertEqual(self.client.post("/api/publish-batch", json={"items": []}).status_code, 401)

    def test_wrong_client_key_rejected(self):
        with patch.dict(os.environ, {"VEBALIST_API_KEY": "test-only-key"}):
            self.assertEqual(self.client.get("/api/status", headers={"X-API-Key": "wrong"}).status_code, 401)

    def test_authorized_validation_reaches_handler(self):
        with patch.dict(os.environ, {"VEBALIST_API_KEY": "test-only-key"}):
            response = self.client.post("/api/validate-listing", json={}, headers={"X-API-Key": "test-only-key"})
            self.assertEqual(response.status_code, 400)
            self.assertIn("Missing fields", response.get_json()["error"])

    def test_publish_requires_explicit_confirmation(self):
        with patch.dict(os.environ, {"VEBALIST_API_KEY": "test-only-key"}):
            headers = {"X-API-Key": "test-only-key"}
            response = self.client.post("/api/publish-batch", json={"items": [{"sku": "TEST"}]}, headers=headers)
            self.assertEqual(response.status_code, 400)
            self.assertIn("confirmation", response.get_json()["error"])

    def test_publish_rejects_empty_batch_even_with_confirmation(self):
        with patch.dict(os.environ, {"VEBALIST_API_KEY": "test-only-key"}):
            response = self.client.post("/api/publish-batch", json={"confirm_publish": True, "items": []}, headers={"X-API-Key": "test-only-key"})
            self.assertEqual(response.status_code, 400)

    def test_invalid_numeric_fields_fail_without_ebay_calls(self):
        from app import validate_listing_payload
        base = {
            "sku": "TEST", "title": "Test listing", "description": "Description",
            "category_id": "15709", "condition": "USED_EXCELLENT",
            "price": "10", "quantity": 1, "payment_policy_id": "p",
            "return_policy_id": "r", "inventory_location_key": "loc",
            "shipping": {"fulfillment_policy_id": "f", "weight_pounds": 1,
                         "package_length": 10, "package_width": 8, "package_height": 4}
        }
        for field, value in (("quantity", "abc"), ("quantity", 1.5),
                             ("quantity", True), ("price", "NaN"),
                             ("price", "Infinity")):
            with self.subTest(field=field, value=value):
                item = dict(base, **{field: value})
                ok, error = validate_listing_payload(item)
                self.assertFalse(ok)
                self.assertTrue(error)
        for field, value in (("weight_pounds", "-1"), ("weight_ounces", "NaN"),
                             ("package_length", 0), ("package_width", "oops"),
                             ("package_height", "-3")):
            with self.subTest(field=field, value=value):
                item = dict(base)
                item["shipping"] = dict(base["shipping"], **{field: value})
                ok, error = validate_listing_payload(item)
                self.assertFalse(ok)
                self.assertTrue(error)
        item = dict(base, shipping="invalid")
        ok, error = validate_listing_payload(item)
        self.assertFalse(ok)
        self.assertTrue(error)

if __name__ == "__main__":
    unittest.main()
