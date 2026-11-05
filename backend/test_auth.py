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

if __name__ == "__main__":
    unittest.main()
