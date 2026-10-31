import os
import unittest
from unittest.mock import patch

os.environ["VEBALIST_API_KEY"] = "test-only-key"
os.environ["VEBALIST_ALLOW_PUBLISH"] = "false"

import app


class SafetyGateTests(unittest.TestCase):
    def setUp(self):
        self.client = app.app.test_client()

    def test_health_is_public_and_minimal(self):
        response = self.client.get("/healthz")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.get_json(), {"ok": True, "service": "vebalist-backend"})

    def test_protected_status_requires_key(self):
        response = self.client.get("/api/status")
        self.assertEqual(response.status_code, 401)

    def test_protected_status_accepts_key_before_handler(self):
        # The handler may fail because no real eBay credentials are present;
        # a non-401 response proves the access gate accepted the key.
        response = self.client.get("/api/status", headers={"X-VEbalist-Key": "test-only-key"})
        self.assertNotEqual(response.status_code, 401)

    @patch("app.ensure_inventory_location")
    @patch("app.ebay_get")
    def test_account_setup_does_not_create_inventory_location(self, ebay_get, ensure_location):
        ebay_get.side_effect = [
            {"paymentPolicies": []},
            {"returnPolicies": []},
            {"fulfillmentPolicies": []},
            {"locations": []},
        ]
        response = self.client.get(
            "/api/ebay/account-setup",
            headers={"X-VEbalist-Key": "test-only-key"},
        )
        self.assertEqual(response.status_code, 200)
        ensure_location.assert_not_called()

    def test_publish_is_disabled_by_default(self):
        response = self.client.post(
            "/api/publish-batch",
            json={"items": [{"sku": "TEST-1"}]},
            headers={"X-VEbalist-Key": "test-only-key"},
        )
        self.assertEqual(response.status_code, 403)
        self.assertIn("disabled", response.get_json()["error"].lower())

    def test_wrong_key_is_rejected(self):
        response = self.client.get("/api/status", headers={"X-VEbalist-Key": "wrong"})
        self.assertEqual(response.status_code, 401)


if __name__ == "__main__":
    unittest.main()
