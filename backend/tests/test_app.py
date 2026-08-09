import unittest
import os

from app import app, ebay_condition


class BackendTests(unittest.TestCase):
    def setUp(self):
        app.config.update(TESTING=True)
        os.environ["VEBALIST_API_KEY"] = "test-key"
        self.client = app.test_client()

    def test_health_does_not_require_ebay_credentials(self):
        for path in ("/health", "/healthz"):
            with self.subTest(path=path):
                response = self.client.get(path)
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

    def test_preowned_apparel_uses_accepted_ebay_condition(self):
        for source_condition in ("USED_EXCELLENT", "USED_VERY_GOOD", "USED_GOOD", "USED_ACCEPTABLE"):
            with self.subTest(source_condition=source_condition):
                self.assertEqual(
                    ebay_condition({"category_id": "53159", "condition": source_condition}),
                    "USED_EXCELLENT",
                )

        self.assertEqual(
            ebay_condition({"category_id": "15559", "condition": "NEW_OTHER"}),
            "NEW_OTHER",
        )


if __name__ == "__main__":
    unittest.main()
