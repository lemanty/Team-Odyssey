#!/usr/bin/env python3
"""Team database commands. Python 3 standard library only; never prints passwords."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)


def config(filename):
    path = ROOT / filename
    if not path.exists():
        raise RuntimeError(f'Prepare {filename} from {filename}.example first; see README.')
    values = {}
    for line in path.read_text().splitlines():
        if line.strip() and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            values[key.strip()] = value.strip()
    return values


def required(values, key):
    value = values.get(key, '')
    if not value:
        raise RuntimeError(f'Missing configuration: {key}')
    return value


def docker():
    binary = shutil.which('docker')
    if not binary and Path('/Applications/Docker.app/Contents/Resources/bin/docker').exists():
        binary = '/Applications/Docker.app/Contents/Resources/bin/docker'
    if not binary:
        raise RuntimeError('Start/install Docker Desktop on the database host first.')
    return binary


def compose(*args, **kwargs):
    return subprocess.run([docker(), 'compose', '--env-file', '.env.mysql',
                           '-f', 'infra/mysql/compose.yaml', *args], check=True, **kwargs)


def sql(statement=None):
    values = config('.env.mysql')
    env = os.environ.copy()
    env['MYSQL_PWD'] = required(values, 'MYSQL_PASSWORD')
    command = ['exec']
    if statement is not None:
        command.append('-T')
    command += ['-e', 'MYSQL_PWD', 'mysql', 'mysql', '--protocol=TCP', '-h127.0.0.1',
                '-uodyssey_app', 'odyssey']
    if statement is None:
        compose(*command, env=env)
    else:
        compose(*command, env=env, input=statement, text=True)


def tunnel():
    values = config('.env.team')
    host = required(values, 'SSH_HOST')
    user = required(values, 'SSH_USER')
    if host.startswith('-') or user.startswith('-'):
        raise RuntimeError('Invalid SSH host/user')
    port = int(values.get('DB_PORT', '13307'))
    if not 1024 <= port <= 65535:
        raise RuntimeError('DB_PORT must be between 1024 and 65535')
    command = ['ssh', '-N', '-o', 'ExitOnForwardFailure=yes', '-o', 'ServerAliveInterval=30',
               '-o', 'ServerAliveCountMax=3', '-L', f'127.0.0.1:{port}:127.0.0.1:3307']
    if values.get('SSH_KEY'):
        command += ['-i', str(Path(values['SSH_KEY']).expanduser()), '-o', 'IdentitiesOnly=yes']
    command += [f'{user}@{host}']
    print(f'Tunnel: 127.0.0.1:{port} -> database host. Keep this window open; Ctrl+C closes it.', flush=True)
    subprocess.run(command, check=True)


def website(role):
    values = config('.env.mysql' if role == 'host' else '.env.team')
    port = int(values.get('MYSQL_PORT', '3307') if role == 'host' else values.get('DB_PORT', '13307'))
    env = os.environ.copy()
    env.update(SPRING_PROFILES_ACTIVE='mysql',
               DB_URL=f'jdbc:mysql://127.0.0.1:{port}/odyssey',
               DB_USERNAME='odyssey_app' if role == 'host' else values.get('DB_USERNAME', 'odyssey_app'),
               DB_PASSWORD=required(values, 'MYSQL_PASSWORD' if role == 'host' else 'DB_PASSWORD'))
    # JAVA_HOME can be supplied by IntelliJ/terminal; otherwise use the installed macOS JDK.
    if sys.platform == 'darwin' and not env.get('JAVA_HOME'):
        java = subprocess.run(['/usr/libexec/java_home', '-v', '17'], capture_output=True, text=True)
        if java.returncode == 0:
            env['JAVA_HOME'] = java.stdout.strip()
    wrapper = str(ROOT / ('mvnw.cmd' if os.name == 'nt' else 'mvnw'))
    subprocess.run([wrapper, 'spring-boot:run'], env=env, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['host-start', 'status', 'login', 'init', 'tunnel', 'web-host', 'web-member'])
    action = parser.parse_args().action
    if action == 'host-start':
        config('.env.mysql')
        compose('up', '-d', '--wait', '--wait-timeout', '360')
        sql('SELECT DATABASE(), CURRENT_USER(), VERSION();')
    elif action == 'status':
        compose('ps')
    elif action == 'login':
        sql()
    elif action == 'init':
        sql((ROOT / 'src/main/resources/db/001-products.sql').read_text())
    elif action == 'tunnel':
        tunnel()
    else:
        website('host' if action == 'web-host' else 'member')


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print('\nStopped.')
    except (RuntimeError, ValueError, FileNotFoundError, subprocess.CalledProcessError) as error:
        print(f'Command failed: {type(error).__name__}. Check configuration, Docker/SSH, and the error above.', file=sys.stderr)
        if isinstance(error, (RuntimeError, FileNotFoundError)):
            print(str(error), file=sys.stderr)
        sys.exit(1)
