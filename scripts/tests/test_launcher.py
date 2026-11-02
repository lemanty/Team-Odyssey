import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('database', Path(__file__).resolve().parents[1] / 'database.py')
db = importlib.util.module_from_spec(spec)
spec.loader.exec_module(db)


class LauncherTests(unittest.TestCase):
    def test_windows_editor_bom_and_chinese_config(self):
        with tempfile.TemporaryDirectory() as root, patch.object(db, 'ROOT', Path(root)):
            (Path(root) / '.env.team').write_text('SSH_HOST=10.0.0.1\nSSH_KEY=C:/Users/组员/.ssh/id_ed25519\n', encoding='utf-8-sig')
            values = db.config('.env.team')
            self.assertEqual(values['SSH_HOST'], '10.0.0.1')
            self.assertEqual(values['SSH_KEY'], 'C:/Users/组员/.ssh/id_ed25519')

    def test_windows_wrapper_uses_fixed_arguments_with_project_cwd(self):
        with patch.dict(db.os.environ, {'COMSPEC': 'C:/Windows/System32/cmd.exe'}):
            self.assertEqual(db.maven_command(True), ['C:/Windows/System32/cmd.exe', '/d', '/c', 'mvnw.cmd', 'spring-boot:run'])

    def test_host_all_stops_before_web_when_database_fails(self):
        with patch.object(db.sys, 'argv', ['database.py', 'host-all']), \
             patch.object(db, 'config', return_value={}), \
             patch.object(db, 'compose', side_effect=RuntimeError('database failed')), \
             patch.object(db, 'sql') as sql, patch.object(db, 'website') as website:
            with self.assertRaises(RuntimeError): db.main()
            sql.assert_not_called()
            website.assert_not_called()

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


class EnvironmentTests(unittest.TestCase):
    def test_config_quoted_windows_path_and_invalid_line(self):
        with tempfile.TemporaryDirectory() as root, patch.object(db, 'ROOT', Path(root)):
            path = Path(root) / '.env.team'
            path.write_text('SSH_KEY="C:/Users/Member Name/.ssh/id_ed25519"\n', encoding='utf-8')
            self.assertEqual(db.config('.env.team')['SSH_KEY'], 'C:/Users/Member Name/.ssh/id_ed25519')
            path.write_text('not-a-key SECRET-VALUE\n', encoding='utf-8')
            with self.assertRaises(RuntimeError) as caught: db.config('.env.team')
            self.assertNotIn('SECRET-VALUE', str(caught.exception))

    def test_timeout_allows_first_download_but_is_bounded(self):
        self.assertEqual(db.startup_timeout({}), 600)
        self.assertEqual(db.startup_timeout({'STARTUP_TIMEOUT':'900'}), 900)
        for value in ['0','999999','oops']:
            with self.assertRaises(RuntimeError): db.startup_timeout({'STARTUP_TIMEOUT':value})

    def test_java_old_or_missing_jdk_explains_fix(self):
        from types import SimpleNamespace
        with patch.object(db.shutil, 'which', return_value='/fake/java'), patch.object(db.subprocess,'run',return_value=SimpleNamespace(returncode=0,stdout='',stderr='java version "1.8.0"')):
            with self.assertRaisesRegex(RuntimeError,'17'): db.check_java({})
        with patch.object(db.shutil,'which',return_value=None):
            with self.assertRaisesRegex(RuntimeError,'JDK'): db.check_java({})

    def test_java_checks_compiler_as_well_as_runtime(self):
        from types import SimpleNamespace
        versions=[SimpleNamespace(returncode=0,stdout='',stderr='openjdk version "21.0.1"'),SimpleNamespace(returncode=0,stdout='javac 21.0.1',stderr='')]
        with patch.object(db.shutil,'which',side_effect=lambda name,**kw:'/fake/'+name), patch.object(db.subprocess,'run',side_effect=versions) as run:
            db.check_java({})
            self.assertEqual(run.call_count,2)

    def test_missing_ssh_has_setup_hint(self):
        with patch.object(db,'config',return_value={'SSH_HOST':'example.test','SSH_USER':'member'}),patch.object(db.shutil,'which',return_value=None):
            with self.assertRaisesRegex(RuntimeError,'Windows-Setup'):db.tunnel()

    def test_windows_entrypoints_and_wrapper_path_quoting(self):
        root = Path(__file__).resolve().parents[2]
        for name in ['Windows-Setup.cmd','Windows-Check.cmd']:
            data=(root/'scripts'/name).read_bytes()
            self.assertIn(b'\r\n',data)
            self.assertIn(b'pause',data)
        wrapper=(root/'mvnw.cmd').read_text()
        self.assertIn('Get-Content -Raw -LiteralPath $env:__MVNW_SCRIPT_FILE__',wrapper)
        self.assertNotIn("$scriptDir='%~dp0'",wrapper)


if __name__ == '__main__':
    unittest.main()
