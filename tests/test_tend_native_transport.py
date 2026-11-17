"""Offline HTTP boundary tests. These do NOT compile or emulate Grubbery."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import URLError

ROOT = Path(__file__).parents[1]
spec = importlib.util.spec_from_file_location("native_transport_test", ROOT / "omarchy-plugin/transport/eyre_client.py")
transport = importlib.util.module_from_spec(spec)
spec.loader.exec_module(transport)


class NativeTransportTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.config = Path(self.temp.name) / "connection.json"
        self.cookie = Path(self.temp.name) / "cookies.txt"
        transport.write_connection(self.config, "http://127.0.0.1:19381", "zod")
        self.command = {"kind": "edit", "action": {"create-list": {"operation-id": "kept-id", "title": "Sentinel"}}}
        self.receipt = {"protocol": "tend-native-1", "operation-id": "kept-id", "outcome": "committed", "reason": "ok"}

    def test_native_write_requires_committed_receipt_and_posts_once(self):
        with patch.object(transport, "json_request", return_value=self.receipt) as request:
            self.assertEqual(transport.native_command(self.config, self.cookie, self.command), self.receipt)
        request.assert_called_once()
        self.assertTrue(request.call_args.args[1].endswith("/apps/tend/api/v1/command"))
        self.assertEqual(request.call_args.kwargs["payload"], self.command)
        self.assertEqual(request.call_args.kwargs["method"], "POST")

    def test_http_acceptance_wrong_id_and_wrong_protocol_remain_uncertain(self):
        for value in (None, {"ok": True}, {**self.receipt, "operation-id": "other"}, {**self.receipt, "protocol": "unknown"}):
            with self.subTest(value=value), patch.object(transport, "json_request", return_value=value):
                with self.assertRaisesRegex(transport.TendTransportError, "uncertain"):
                    transport.native_command(self.config, self.cookie, self.command)

    def test_transport_loss_preserves_id_without_retrying_or_gall_fallback(self):
        with patch.object(transport, "json_request", side_effect=URLError("reply lost")) as request:
            with self.assertRaisesRegex(transport.TendTransportError, "kept-id.*uncertain"):
                transport.native_command(self.config, self.cookie, self.command)
        request.assert_called_once()
        self.assertEqual(self.command["action"]["create-list"]["operation-id"], "kept-id")

    def test_rejected_receipt_is_a_canonical_outcome(self):
        value = {**self.receipt, "outcome": "rejected", "reason": "stale-list"}
        with patch.object(transport, "json_request", return_value=value):
            self.assertEqual(transport.native_command(self.config, self.cookie, self.command), value)

    def test_polling_snapshot_requires_native_protocol(self):
        for version in (None, 2, 3, 4):
            with self.subTest(version=version), patch.object(transport, "json_request", return_value={"snapshot": {"protocol-version": version, "lists": []}}):
                if version == 3:
                    transport.native_read(self.config, self.cookie)
                else:
                    with self.assertRaises(transport.TendTransportError):
                        transport.native_read(self.config, self.cookie)

    def test_receipt_lookup_cannot_change_route(self):
        for path in ("../state", "receipt/../../private", "receipt/one?other=1"):
            with self.subTest(path=path), self.assertRaises(transport.TendTransportError):
                transport.native_read(self.config, self.cookie, path)

    def test_ids_required_before_any_network_effect(self):
        invalid = [None, {}, {"kind": "edit", "action": {}}, {"kind": "annotate", "operation-id": ""}]
        for command in invalid:
            with self.subTest(command=command), patch.object(transport, "json_request") as request:
                with self.assertRaises(transport.TendTransportError):
                    transport.native_command(self.config, self.cookie, command)
                request.assert_not_called()


if __name__ == "__main__":
    unittest.main()
