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

    def test_allowlisted_google_id_token_is_accepted(self):
        env = {
            "VEBALIST_GOOGLE_CLIENT_ID": "web-client-id",
            "VEBALIST_ALLOWED_GOOGLE_EMAILS": "owner@example.com",
        }
        claims = {"email": "owner@example.com", "email_verified": True}
        with patch.dict(os.environ, env, clear=True), \
             patch("app.id_token.verify_oauth2_token", return_value=claims) as verify, \
             patch("app.access_token", return_value="test-ebay-token"):
            response = self.client.get("/api/status", headers={"Authorization": "Bearer signed-id-token"})
        self.assertEqual(response.status_code, 200)
        verify.assert_called_once()
        self.assertEqual(verify.call_args.kwargs["audience"], "web-client-id")

    def test_google_id_token_requires_verified_allowlisted_email(self):
        env = {
            "VEBALIST_GOOGLE_CLIENT_ID": "web-client-id",
            "VEBALIST_ALLOWED_GOOGLE_EMAILS": "owner@example.com",
        }
        claims = {"email": "other@example.com", "email_verified": True}
        with patch.dict(os.environ, env, clear=True), \
             patch("app.id_token.verify_oauth2_token", return_value=claims):
            response = self.client.get("/api/status", headers={"Authorization": "Bearer signed-id-token"})
        self.assertEqual(response.status_code, 401)

    def test_google_id_token_requires_auth_configuration(self):
        with patch.dict(os.environ, {"VEBALIST_GOOGLE_CLIENT_ID": "web-client-id"}, clear=True):
            response = self.client.get("/api/status", headers={"Authorization": "Bearer signed-id-token"})
        self.assertEqual(response.status_code, 503)

    def test_invalid_google_id_token_is_rejected(self):
        env = {
            "VEBALIST_GOOGLE_CLIENT_ID": "web-client-id",
            "VEBALIST_ALLOWED_GOOGLE_EMAILS": "owner@example.com",
        }
        with patch.dict(os.environ, env, clear=True), \
             patch("app.id_token.verify_oauth2_token", side_effect=ValueError("invalid token")):
            response = self.client.get("/api/status", headers={"Authorization": "Bearer invalid-token"})
        self.assertEqual(response.status_code, 401)

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

    def test_duplicate_skus_rejected_before_network(self):
        with patch.dict(os.environ, {"VEBALIST_API_KEY": "test-only-key"}):
            with patch("app.ebay_get", side_effect=AssertionError("Unexpected eBay call")):
                response = self.client.post("/api/publish-batch",
                    json={"confirm_publish": True, "items": [{"sku": "DUP"}, {"sku": "DUP"}]},
                    headers={"X-API-Key": "test-only-key"})
                self.assertEqual(response.status_code, 400)
                self.assertIn("Duplicate", response.get_json()["error"])

    def test_oversized_batch_rejected_before_network(self):
        with patch.dict(os.environ, {"VEBALIST_API_KEY": "test-only-key"}):
            with patch("app.ebay_get", side_effect=AssertionError("Unexpected eBay call")):
                response = self.client.post("/api/publish-batch",
                    json={"confirm_publish": True, "items": [{"sku": str(i)} for i in range(26)]},
                    headers={"X-API-Key": "test-only-key"})
                self.assertEqual(response.status_code, 400)
                self.assertIn("25", response.get_json()["error"])

    def test_listing_must_be_object(self):
        from app import validate_listing_payload
        self.assertFalse(validate_listing_payload(None)[0])
        self.assertFalse(validate_listing_payload([])[0])

if __name__ == "__main__":
    unittest.main()
