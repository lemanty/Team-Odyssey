import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('database', Path(__file__).resolve().parents[1] / 'database.py')
db = importlib.util.module_from_spec(spec)
spec.loader.exec_module(db)


class LauncherTests(unittest.TestCase):
    def test_existing_mysql_opens_without_starting_java(self):
        with tempfile.TemporaryDirectory() as root, patch.object(db, 'ROOT', Path(root)), \
             patch.object(db, 'website_state', return_value='mysql'), \
             patch.object(db, 'open_website') as browser, patch.object(db.subprocess, 'Popen') as start:
            db.run_website(['unused'], {}, 18086)
            browser.assert_called_once_with(18086)
            start.assert_not_called()

    def test_demo_or_other_service_is_never_reused_or_killed(self):
        for state in ['demo', 'occupied']:
            with tempfile.TemporaryDirectory() as root, patch.object(db, 'ROOT', Path(root)), \
                 patch.object(db, 'website_state', return_value=state), \
                 patch.object(db, 'open_website') as browser, patch.object(db.subprocess, 'Popen') as start:
                with self.assertRaises(RuntimeError):
                    db.run_website(['unused'], {}, 18086)
                start.assert_not_called()
                browser.assert_not_called()

    def test_lock_is_exclusive_and_released(self):
        with tempfile.TemporaryDirectory() as root, patch.object(db, 'ROOT', Path(root)):
            with db.launch_lock(18086) as first:
                self.assertTrue(first)
                with db.launch_lock(18086) as second:
                    self.assertFalse(second)
            with db.launch_lock(18086) as third:
                self.assertTrue(third)


if __name__ == '__main__':
    unittest.main()
