import importlib.util
import io
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('contabo_deploy', Path(__file__).parents[1] / 'deploy.py')
deployment = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deployment)


class DeploymentBoundaries(unittest.TestCase):
    def test_only_scoped_commands_are_accepted(self):
        self.assertIsNone(deployment.parse_command('status'))
        commit = 'a' * 40
        self.assertEqual(deployment.parse_command('deploy ' + commit), commit)
        for command in ['bash', 'deploy main', 'deploy ' + commit + '; id', 'deploy ' + commit + '\n', 'scp -t /root']:
            with self.subTest(command=command), self.assertRaises(ValueError):
                deployment.parse_command(command)

    def archive(self, path, name, kind=None):
        with tarfile.open(path, 'w:gz') as output:
            entry = tarfile.TarInfo(name)
            if kind:
                entry.type = kind
                entry.linkname = '/opt/lms/shared/.env'
                output.addfile(entry)
            else:
                content = b'approved source'
                entry.size = len(content)
                output.addfile(entry, io.BytesIO(content))

    def test_paths_links_and_private_files_cannot_escape_source(self):
        for name, kind in [('../shared/.env', None), ('/root/.ssh/authorized_keys', None),
                           ('backend/../secrets', None), ('frontend\\escape', None),
                           ('.env', None), ('output/credentials.json', None),
                           ('backend/data-link', tarfile.SYMTYPE), ('frontend/link', tarfile.LNKTYPE)]:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                self.archive(root / 'source.tgz', name, kind)
                target = root / 'release'
                target.mkdir()
                with self.assertRaises(ValueError):
                    deployment.extract_source(root / 'source.tgz', target)
                self.assertEqual(list(target.iterdir()), [])

    def test_regular_source_extracts_with_readable_permissions(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            self.archive(root / 'source.tgz', 'backend/src/source.java')
            target = root / 'release'
            target.mkdir()
            deployment.extract_source(root / 'source.tgz', target)
            self.assertEqual((target / 'backend/src/source.java').read_bytes(), b'approved source')


if __name__ == '__main__':
    unittest.main()
