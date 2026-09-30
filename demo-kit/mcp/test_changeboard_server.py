import io
import json
import os
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import changeboard_server as cb  # noqa: E402


def call(board, name, args, mid=1):
    return cb.handle(board, {"jsonrpc": "2.0", "id": mid, "method": "tools/call", "params": {"name": name, "arguments": args}})


class ChangeboardTest(unittest.TestCase):
    def setUp(self):
        os.environ.pop("CHANGEBOARD_PERSIST", None)
        self.board = cb.Board(Path(__file__).resolve().parent / "tickets.json")

    def test_handshake_lists_five_tools(self):
        out = io.StringIO()
        cb.serve(open(Path(__file__).resolve().parent / "handshake.jsonl"), out, self.board)
        lines = [json.loads(line) for line in out.getvalue().splitlines()]
        self.assertEqual(lines[0]["result"]["serverInfo"]["name"], "changeboard")
        self.assertEqual(len(lines[1]["result"]["tools"]), 5)
        self.assertIn("TRON-101", lines[2]["result"]["content"][0]["text"])
        self.assertEqual(lines[3]["error"]["code"], -32602)

    def test_ticket_marked_untrusted(self):
        text = call(self.board, "get_ticket", {"id": "TRON-104"})["result"]["content"][0]["text"]
        self.assertTrue(json.loads(text)["untrusted_content"])

    def test_comment_with_credential_rejected(self):
        r = call(self.board, "add_ticket_comment", {"id": "TRON-101", "body": "-----BEGIN OPENSSH PRIVATE KEY----- abc"})
        self.assertTrue(r["result"]["isError"])

    def test_comment_is_in_memory_by_default(self):
        r = call(self.board, "add_ticket_comment", {"id": "TRON-101", "body": "plan posted"})
        self.assertFalse(r["result"]["isError"])
        on_disk = json.loads((Path(__file__).resolve().parent / "tickets.json").read_text())
        self.assertEqual(on_disk["tickets"][0]["comments"], [])

    def test_unknown_argument_rejected(self):
        r = call(self.board, "list_tickets", {"owner": "x"})
        self.assertEqual(r["error"]["code"], -32602)


if __name__ == "__main__":
    unittest.main()
