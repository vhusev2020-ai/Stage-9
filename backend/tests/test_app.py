import unittest
import os

from app import app


class BackendTests(unittest.TestCase):
    def setUp(self):
        app.config.update(TESTING=True)
        os.environ["VEBALIST_API_KEY"] = "test-key"
        self.client = app.test_client()

    def test_health_does_not_require_ebay_credentials(self):
        response = self.client.get("/healthz")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.get_json()["service"], "vebalist-backend")

    def test_validation_rejects_incomplete_listing(self):
        response = self.client.post(
            "/api/validate-listing",
            json={"sku": "TEST-1"},
            headers={"X-VEbalist-Key": "test-key"},
        )
        self.assertEqual(response.status_code, 400)
        body = response.get_json()
        self.assertFalse(body["ok"])
        self.assertIn("Missing fields", body["error"])

    def test_protected_routes_require_app_key(self):
        response = self.client.get("/api/status")
        self.assertEqual(response.status_code, 401)


if __name__ == "__main__":
    unittest.main()
