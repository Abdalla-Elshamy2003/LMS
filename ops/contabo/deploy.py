#!/usr/bin/env python3
"""Forced SSH command for the existing LMS Compose project on Contabo."""
import datetime
import json
import os
from pathlib import Path, PurePosixPath
import re
import signal
import subprocess
import sys
import tarfile
import time
from urllib.error import HTTPError
from urllib.request import Request, urlopen

ROOT = Path('/opt/lms')
PUBLIC_URL = 'https://droos.com.co'
MAX_ARCHIVE_BYTES = 128 * 1024 * 1024
MAX_EXPANDED_BYTES = 1024 * 1024 * 1024


def parse_command(command):
    if command == 'status':
        return None
    match = re.fullmatch(r'deploy ([0-9a-f]{40})', command)
    if not match:
        raise ValueError('Only status or deploy <40-character commit> is allowed')
    return match.group(1)


def extract_source(archive, destination):
    with tarfile.open(archive, 'r:gz') as source:
        members = source.getmembers()
        if sum(member.size for member in members) > MAX_EXPANDED_BYTES:
            raise ValueError('Source archive is too large')
        for member in members:
            name = PurePosixPath(member.name)
            if name.is_absolute() or '..' in name.parts or '\\' in member.name:
                raise ValueError('Unsafe source path')
            if not (member.isdir() or member.isfile()):
                raise ValueError('Source links and special files are not accepted')
            if any(part in {'.git', '.env', 'tmp', 'output', 'node_modules', 'target'} for part in name.parts):
                raise ValueError('Source archive contains local data or credentials')
        for member in members:
            target = destination.joinpath(*PurePosixPath(member.name).parts)
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
                target.chmod(0o755)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with source.extractfile(member) as reader, target.open('wb') as writer:
                    while chunk := reader.read(1024 * 1024):
                        writer.write(chunk)
                target.chmod(0o755 if member.mode & 0o111 else 0o644)


def command(args, **kwargs):
    subprocess.run(args, check=True, **kwargs)


def container(service):
    return json.loads(subprocess.check_output(['docker', 'inspect', 'lms-contabo-' + service + '-1']))[0]


def status():
    return {service: container(service)['State']['Health']['Status']
            for service in ['backend', 'frontend', 'postgres', 'redis']}


def compose(release, *args):
    command(['docker', 'compose', '--ansi', 'never', '-f', str(release / 'compose.production.yml'), *args])


def wait_healthy(service):
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline:
        state = container(service)['State']
        if state['Health']['Status'] == 'healthy':
            return
        if not state['Running']:
            raise RuntimeError(service + ' stopped during deployment')
        time.sleep(5)
    raise RuntimeError(service + ' did not become healthy')


def get(path):
    with urlopen(Request('http://127.0.0.1:18475' + path,
                         headers={'User-Agent': 'LMS Contabo release verification'}), timeout=30) as response:
        return response.read()


def deploy(commit):
    shared = ROOT / 'shared'
    previous = (ROOT / 'current').resolve(strict=True)
    if previous.parent != ROOT / 'releases':
        raise RuntimeError('Unexpected current release path')
    if '213.199.38.162' not in subprocess.check_output(['hostname', '-I'], text=True).split():
        raise RuntimeError('Deployment is limited to the LMS Contabo server')
    before_backend = container('backend')
    previous_env = dict(item.split('=', 1) for item in before_backend['Config']['Env'])
    if previous_env.get('MANARAH_STORAGE_PROVIDER') != 's3' or previous_env.get('MANARAH_VIDEO_STORAGE') != 'r2':
        raise RuntimeError('The expected R2 production configuration is not active')
    postgres_mounts = container('postgres')['Mounts']
    release = ROOT / 'releases' / ('github-' + commit)
    archive = ROOT / 'incoming' / (commit + '.tar.gz')
    size = 0
    with archive.open('wb') as output:
        while chunk := sys.stdin.buffer.read(1024 * 1024):
            size += len(chunk)
            if size > MAX_ARCHIVE_BYTES:
                raise ValueError('Compressed source archive is too large')
            output.write(chunk)
    if release.is_symlink():
        raise RuntimeError('A release directory cannot be a symbolic link')
    marker = release / '.source-commit'
    if release.exists():
        if not marker.is_file() or marker.read_text().strip() != commit:
            raise RuntimeError('An existing release was not created by this deployment command')
    else:
        release.mkdir()
        marker.write_text(commit + '\n')
    extract_source(archive, release)
    template = (release / 'ops/contabo/compose.production.yml').read_text()
    (release / 'compose.production.yml').write_text(template.replace('__RELEASE_SHA__', commit))
    env_link = release / '.env'
    if env_link.is_symlink():
        if env_link.resolve() != shared / '.env':
            raise RuntimeError('Unexpected release environment link')
    else:
        env_link.symlink_to(shared / '.env')
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    metadata = {'commit': commit, 'origin': 'contabo', 'builtAt': stamp}
    (release / 'frontend/public/release.json').write_text(json.dumps(metadata) + '\n')
    (release / 'frontend/public/release.json').chmod(0o644)
    compose(release, 'config', '--quiet')
    for service in ['backend', 'frontend']:
        command(['docker', 'build', '--label', 'org.opencontainers.image.revision=' + commit,
                 '-t', 'lms-' + service + ':' + commit, str(release / service)])
    backup = ROOT / 'backups' / ('before-' + commit[:12] + '-' + stamp + '.dump')
    with backup.open('wb') as output:
        command(['docker', 'exec', 'lms-contabo-postgres-1', 'pg_dump', '-U', 'manarah', '-d', 'manarah', '-Fc'], stdout=output)
    backup.chmod(0o600)
    if backup.stat().st_size < 1024:
        raise RuntimeError('Database backup was not created')
    changed = False
    try:
        changed = True
        compose(release, 'up', '-d', '--no-deps', 'backend')
        wait_healthy('backend')
        compose(release, 'up', '-d', '--no-deps', 'frontend')
        wait_healthy('frontend')
        if container('postgres')['Mounts'] != postgres_mounts:
            raise RuntimeError('PostgreSQL storage changed unexpectedly')
        active_env = dict(item.split('=', 1) for item in container('backend')['Config']['Env'])
        preserved = ['MANARAH_STORAGE_PROVIDER', 'MANARAH_VIDEO_STORAGE', 'MANARAH_R2_ENDPOINT',
                     'MANARAH_R2_BUCKET', 'MANARAH_R2_ACCESS_KEY_ID', 'MANARAH_R2_SECRET_ACCESS_KEY',
                     'MANARAH_AI_API_KEY', 'MANARAH_FAWATERAK_API_KEY', 'MANARAH_FAWATERAK_VENDOR_KEY', 'MANARAH_JWT_SECRET']
        if any(active_env.get(key) != previous_env.get(key) for key in preserved):
            raise RuntimeError('An existing service setting was changed unexpectedly')
        assert get('/healthz').strip() == b'ok'
        assert isinstance(json.loads(get('/api/public/home'))['teachers'], list)
        assert json.loads(get('/release.json'))['commit'] == commit
        try:
            get('/api/files/not-an-upload.pdf')
            raise RuntimeError('Anonymous file access was not denied')
        except HTTPError as error:
            if error.code != 401:
                raise
        for service in ['backend', 'frontend']:
            if container(service)['Config']['Labels'].get('org.opencontainers.image.revision') != commit:
                raise RuntimeError('A container is running a different revision')
        link = ROOT / 'current.next'
        if link.exists() or link.is_symlink():
            link.unlink()
        link.symlink_to(release)
        os.replace(link, ROOT / 'current')
        result = {**metadata, 'status': 'success', 'previousRelease': str(previous),
                  'release': str(release), 'databaseBackup': str(backup), 'services': status()}
        (shared / 'last-deployment.json').write_text(json.dumps(result, indent=2) + '\n')
        print(json.dumps(result), flush=True)
    except Exception:
        if changed:
            print('Release verification failed; restoring the previous application images.', flush=True)
            compose(previous, 'up', '-d', '--no-deps', 'backend', 'frontend')
            wait_healthy('backend')
            wait_healthy('frontend')
            rollback_link = ROOT / 'current.rollback'
            if rollback_link.exists() or rollback_link.is_symlink():
                rollback_link.unlink()
            rollback_link.symlink_to(previous)
            os.replace(rollback_link, ROOT / 'current')
        raise


def main():
    import fcntl
    commit = parse_command(os.environ.get('SSH_ORIGINAL_COMMAND', ''))
    os.umask(0o077)
    if commit is None:
        print(json.dumps({'release': str((ROOT / 'current').resolve()), 'services': status()}))
        return
    with (ROOT / 'shared/deploy.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        def interrupted(*_):
            raise RuntimeError('Deployment interrupted')
        signal.signal(signal.SIGHUP, interrupted)
        signal.signal(signal.SIGTERM, interrupted)
        deploy(commit)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('LMS deployment failed: ' + str(error), file=sys.stderr)
        sys.exit(1)
