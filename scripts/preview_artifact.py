"""Keep a running JVM's nested JARs independent from subsequent Gradle builds."""
import hashlib
import os
from pathlib import Path
import tempfile


def snapshot_jar(source: Path, releases: Path) -> Path:
    payload = source.read_bytes()
    digest = hashlib.sha256(payload).hexdigest()
    releases.mkdir(parents=True, exist_ok=True)
    target = releases / f"server-{digest}.jar"
    if target.exists():
        if hashlib.sha256(target.read_bytes()).hexdigest() != digest:
            raise RuntimeError("Preview runtime artifact is damaged; stop preview before repairing it")
        return target
    with tempfile.NamedTemporaryFile(dir=releases, delete=False) as temporary:
        temporary.write(payload)
        temporary_path = Path(temporary.name)
    temporary_path.chmod(0o400)
    os.replace(temporary_path, target)
    return target
