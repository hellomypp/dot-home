import tempfile
import unittest
from pathlib import Path
from preview_artifact import snapshot_jar


class PreviewArtifactTest(unittest.TestCase):
    def test_rebuild_keeps_running_release_and_creates_a_separate_next_release(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            build = root / 'build.jar'
            build.write_bytes(b'first build')
            running = snapshot_jar(build, root / 'releases')
            self.assertEqual(snapshot_jar(build, root / 'releases'), running)
            build.write_bytes(b'next build')
            next_release = snapshot_jar(build, root / 'releases')
            self.assertNotEqual(running, next_release)
            self.assertEqual(running.read_bytes(), b'first build')
            self.assertEqual(next_release.read_bytes(), b'next build')
            self.assertEqual(running.stat().st_mode & 0o222, 0)

    def test_damaged_running_release_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            build = root / 'build.jar'
            build.write_bytes(b'first build')
            running = snapshot_jar(build, root / 'releases')
            running.chmod(0o600)
            running.write_bytes(b'damaged')
            with self.assertRaises(RuntimeError):
                snapshot_jar(build, root / 'releases')
            self.assertEqual(running.read_bytes(), b'damaged')


if __name__ == '__main__':
    unittest.main()
