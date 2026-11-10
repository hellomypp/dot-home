"""Replace only the launchd backend of an already running local H2 preview."""
import fcntl
import os
import plistlib
import re
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.request
from datetime import datetime
from pathlib import Path

from preview_artifact import snapshot_jar


def write_plist(path, payload):
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as temporary:
        temporary.write(payload)
        pending = Path(temporary.name)
    try:
        os.replace(pending, path)
    finally:
        pending.unlink(missing_ok=True)


def switch_release(plist, database, backups, release, stop, start):
    original = plist.read_bytes()
    config = plistlib.loads(original)
    config["EnvironmentVariables"]["DOT_HOME_PREVIEW_JAR"] = str(release)
    backups.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S-")
    backup = Path(tempfile.mkdtemp(prefix=stamp, dir=backups))
    write_plist(backup / plist.name, original)
    stop()
    try:
        snapshot = backup / database.name
        shutil.copyfile(database, snapshot)
        snapshot.chmod(0o600)
        write_plist(plist, plistlib.dumps(config))
        start()
    except Exception as failed:
        try:
            stop()
            write_plist(plist, original)
            start()
        except Exception as rollback:
            raise RuntimeError(f"이전 미리보기 기동도 실패했습니다. 백업 폴더를 확인하세요. {backup}") from rollback
        raise RuntimeError(f"새 미리보기 기동에 실패해 이전 설정을 다시 실행했습니다. {backup}") from failed
    return backup


def upgrade_preview(root, launch_agents, origin):
    label = "kr.dothome.preview.server"
    domain = f"gui/{os.getuid()}"
    service = f"{domain}/{label}"
    plist = launch_agents / f"{label}.plist"
    database = root / "apps/server/data/dothome.mv.db"
    logs = root / ".tools/remote-preview"
    logs.mkdir(parents=True, exist_ok=True)

    def launch(*args):
        return subprocess.run(["launchctl", *args], capture_output=True, text=True)

    def db_open(pid=None):
        args = ["lsof", "-t"]
        if pid is not None:
            args += ["-a", "-p", pid]
        return subprocess.run([*args, str(database)], capture_output=True).returncode == 0

    def job_pid():
        job = launch("print", service)
        match = re.search(r"^\s*pid = (\d+)$", job.stdout, re.MULTILINE)
        return match.group(1) if job.returncode == 0 and match else None

    def stop():
        launch("bootout", service)
        for _ in range(40):
            with socket.socket() as connection:
                port_free = connection.connect_ex(("127.0.0.1", 8080)) != 0
            if launch("print", service).returncode != 0 and not db_open() and port_free:
                time.sleep(1)
                return
            time.sleep(0.5)
        raise RuntimeError("미리보기 서버와 H2 파일이 정상 종료되지 않았습니다.")

    def start():
        for _ in range(8):
            if launch("bootstrap", domain, str(plist)).returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError("미리보기 LaunchAgent를 불러오지 못했습니다.")
        for _ in range(60):
            pid = job_pid()
            if pid and db_open(pid):
                try:
                    with urllib.request.urlopen("http://127.0.0.1:8080/healthz", timeout=1) as response:
                        if response.status == 200:
                            return
                except OSError:
                    pass
            time.sleep(0.5)
        raise RuntimeError("미리보기 서버의 DB 상태 확인에 실패했습니다.")

    with (logs / ".upgrade.lock").open("a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise RuntimeError("이미 다른 미리보기 업데이트가 실행 중입니다.") from error
        if not plist.is_file() or plist.is_symlink() or not database.is_file():
            raise RuntimeError("실행 중인 로컬 H2 미리보기에서만 업데이트할 수 있습니다.")
        config = plistlib.loads(plist.read_bytes())
        env = config.get("EnvironmentVariables", {})
        release = Path(env.get("DOT_HOME_PREVIEW_JAR", ""))
        if (config.get("Label") != label or config.get("WorkingDirectory") != str(root)
            or config.get("ProgramArguments") != ["/bin/bash", str(root / "scripts/server.sh"), "preview"]
            or env.get("APP_ORIGIN") != origin or release.parent != logs / "releases" or not release.is_file()):
            raise RuntimeError("현재 미리보기 실행 설정을 확인해 주세요.")
        pid = job_pid()
        if not pid or not db_open(pid):
            raise RuntimeError("미리보기 LaunchAgent가 기본 로컬 H2 파일을 사용 중이어야 합니다.")
        subprocess.run(["/bin/bash", str(root / "scripts/server.sh"), "bootJar"], check=True)
        next_release = snapshot_jar(root / "apps/server/build/libs/dot-home-server-0.1.0.jar", logs / "releases")
        if next_release == release:
            print("현재 미리보기 서버에 같은 JAR가 적용되어 있습니다.")
            return
        backup = switch_release(plist, database, root / ".tools/preview-upgrade-backups", next_release, stop, start)
        print(f"미리보기 서버 업데이트 완료. 이전 설정과 H2 백업을 저장했습니다. {backup}")
