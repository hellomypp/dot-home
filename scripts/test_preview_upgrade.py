import plistlib
import tempfile
import unittest
from pathlib import Path

from preview_upgrade import switch_release


class PreviewUpgradeTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.plist = self.root / "server.plist"
        self.original = plistlib.dumps({
            "EnvironmentVariables": {"DOT_HOME_PREVIEW_JAR": "old.jar", "APP_ORIGIN": "https://preview.example.test"},
        })
        self.plist.write_bytes(self.original)
        self.db = self.root / "dothome.mv.db"
        self.db.write_bytes(b"existing preview data")
        self.backups = self.root / "backups"

    def test_switches_only_after_stop_and_keeps_a_private_backup(self):
        def stop():
            self.assertEqual(self.plist.read_bytes(), self.original)

        def start():
            variables = plistlib.loads(self.plist.read_bytes())["EnvironmentVariables"]
            self.assertEqual(variables["DOT_HOME_PREVIEW_JAR"], "new.jar")
            self.assertEqual(variables["APP_ORIGIN"], "https://preview.example.test")
            self.assertEqual(self.plist.stat().st_mode & 0o777, 0o600)

        backup = switch_release(self.plist, self.db, self.backups, "new.jar", stop, start)
        self.assertEqual((backup / self.plist.name).read_bytes(), self.original)
        self.assertEqual((backup / self.db.name).read_bytes(), b"existing preview data")
        self.assertEqual(backup.stat().st_mode & 0o777, 0o700)

    def test_failing_health_restores_the_old_launcher_without_overwriting_the_db(self):
        events = []

        def stop():
            events.append("stop")

        def start():
            jar = plistlib.loads(self.plist.read_bytes())["EnvironmentVariables"]["DOT_HOME_PREVIEW_JAR"]
            events.append(jar)
            if jar == "new.jar":
                self.db.write_bytes(b"newer preview data")
                raise RuntimeError("failed health probe")

        with self.assertRaisesRegex(RuntimeError, "이전 설정을 다시 실행"):
            switch_release(self.plist, self.db, self.backups, "new.jar", stop, start)
        self.assertEqual(events, ["stop", "new.jar", "stop", "old.jar"])
        self.assertEqual(self.plist.read_bytes(), self.original)
        self.assertEqual(self.db.read_bytes(), b"newer preview data")
        self.assertEqual(next(self.backups.glob("*/dothome.mv.db")).read_bytes(), b"existing preview data")


if __name__ == "__main__":
    unittest.main()
