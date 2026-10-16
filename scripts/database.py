#!/usr/bin/env python3
"""Team database commands. Python 3 standard library only; never prints passwords."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import signal
import socket
import time
import urllib.request
import urllib.error
import webbrowser
from contextlib import contextmanager

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


def website_state(port):
    """Never assume that an arbitrary service on this port is our website."""
    url = f'http://127.0.0.1:{port}/products'
    try:
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(url, timeout=2) as response:
            if response.status == 200 and response.headers.get('X-Odyssey-App') == 'retail-shop':
                return response.headers.get('X-Odyssey-Mode', 'unknown')
    except (OSError, urllib.error.URLError):
        pass
    try:
        with socket.create_connection(('127.0.0.1', port), timeout=0.5):
            return 'occupied'
    except OSError:
        return 'free'


def open_website(port):
    url = f'http://localhost:{port}/products'
    print(f'网站已就绪：{url}', flush=True)
    if not webbrowser.open(url):
        print('无法自动打开浏览器，请点击上面的地址。', flush=True)


@contextmanager
def launch_lock(port):
    # OS releases the lock on crash; leaving the file behind does not block restart.
    directory = ROOT / '.run'
    directory.mkdir(exist_ok=True)
    with (directory / f'website-{port}.lock').open('a+b') as handle:
        handle.seek(0)
        if os.name == 'nt':
            import msvcrt
            if handle.read(1) == b'':
                handle.write(b'0')
                handle.flush()
            handle.seek(0)
            lock = lambda: msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
            unlock = lambda: msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
        else:
            import fcntl
            lock = lambda: fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
            unlock = lambda: fcntl.flock(handle, fcntl.LOCK_UN)
        try:
            lock()
        except OSError:
            yield False
        else:
            try:
                yield True
            finally:
                handle.seek(0)
                unlock()


def stop_website(process):
    # Stop only the Maven/Java process tree created by THIS launcher.
    if os.name == 'nt':
        if process.poll() is None:
            subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    else:
        try:
            os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError:
            return
        deadline = time.monotonic() + 8
        while time.monotonic() < deadline:
            process.poll()
            try:
                os.killpg(process.pid, 0)
            except ProcessLookupError:
                return
            time.sleep(0.1)
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
    process.wait()


def run_website(command, env, port):
    with launch_lock(port) as acquired:
        if not acquired:
            print('另一个启动脚本正在运行，等待它就绪；不会重复启动。', flush=True)
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline:
                if website_state(port) == 'mysql':
                    open_website(port)
                    return
                time.sleep(1)
            raise RuntimeError('等待已有启动任务超时，请查看原来的终端。')
        state = website_state(port)
        if state == 'mysql':
            print('MySQL 模式网站已运行，直接打开已有页面。', flush=True)
            open_website(port)
            return
        if state != 'free':
            raise RuntimeError(f'端口 {port} 已被占用（{state}）。请先停止旧网站再运行；不会自动结束其他程序。')
        process = subprocess.Popen(command, env=env, start_new_session=(os.name != 'nt'))
        try:
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError('网站在就绪前退出，请查看上方启动日志。')
                if website_state(port) == 'mysql':
                    open_website(port)
                    print('保持此窗口打开。Ctrl+C 会停止本次网站及其子进程，数据库继续运行。', flush=True)
                    code = process.wait()
                    if code:
                        raise RuntimeError(f'网站进程退出，状态码 {code}。')
                    return
                time.sleep(1)
            raise RuntimeError('网站启动超过 180 秒，已停止本次启动，请检查日志后重试。')
        finally:
            stop_website(process)


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
    web_port = int(env.get('PORT', '8080'))
    if not 1024 <= web_port <= 65535:
        raise RuntimeError('PORT must be between 1024 and 65535')
    run_website([wrapper, 'spring-boot:run'], env, web_port)


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
        for migration in sorted((ROOT / 'src/main/resources/db').glob('*.sql')):
            sql(migration.read_text())
    elif action == 'tunnel':
        tunnel()
    else:
        website('host' if action == 'web-host' else 'member')


if __name__ == '__main__':
    if os.name != 'nt':
        def interrupted(signum, frame):
            raise KeyboardInterrupt
        signal.signal(signal.SIGTERM, interrupted)
        signal.signal(signal.SIGHUP, interrupted)
    try:
        main()
    except KeyboardInterrupt:
        print('\nStopped.')
    except (RuntimeError, ValueError, FileNotFoundError, subprocess.CalledProcessError) as error:
        print(f'Command failed: {type(error).__name__}. Check configuration, Docker/SSH, and the error above.', file=sys.stderr)
        if isinstance(error, (RuntimeError, FileNotFoundError)):
            print(str(error), file=sys.stderr)
        sys.exit(1)
