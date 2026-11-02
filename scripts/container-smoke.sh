#!/usr/bin/env bash
# Isolated CI containers/volume only. Never connects to an existing database.
set -euo pipefail
image="${1:-dot-home:ci}"
tag="dothome-ci-$$"
network="$tag-net"; database="$tag-db"; app="$tag-app"; volume="$tag-data"
artifact_dir=$(mktemp -d)
password=$(openssl rand -hex 24)
if [[ "${GITHUB_ACTIONS:-}" == true ]]; then echo "::add-mask::$password"; fi
cleanup(){ docker rm -f "$app" "$database" >/dev/null 2>&1 || true; docker network rm "$network" >/dev/null 2>&1 || true; docker volume rm "$volume" >/dev/null 2>&1 || true; rm -rf "$artifact_dir"; }
trap cleanup EXIT
docker network create "$network" >/dev/null
docker volume create "$volume" >/dev/null
docker run -d --name "$database" --network "$network" --network-alias mysql -v "$volume:/var/lib/mysql" -e MYSQL_ROOT_PASSWORD="$password" -e MYSQL_DATABASE=dothome mysql:8.4.7 >/dev/null
sql(){ docker exec -e MYSQL_PWD="$password" "$database" mysql -h127.0.0.1 -uroot -Nse "$1"; }
wait_db(){ for attempt in $(seq 1 90); do if sql 'SELECT 1' >/dev/null 2>&1; then return; fi; sleep 2; done; echo 'Isolated MySQL did not start' >&2; return 1; }
wait_db
docker run -d --name "$app" --network "$network" -p 127.0.0.1::8080 \
 -e PORT=8080 -e APP_ORIGIN=https://beta.example.org \
 -e 'DB_URL=jdbc:mysql://mysql:3306/dothome?serverTimezone=UTC&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=10000' \
 -e DB_USER=root -e DB_PASSWORD="$password" \
 -e GOOGLE_CLIENT_ID=container-test.apps.googleusercontent.com -e GOOGLE_CLIENT_SECRET=container-test-only "$image" >/dev/null
host_port=$(docker port "$app" 8080/tcp); base="http://$host_port"
wait_app(){ for attempt in $(seq 1 90); do if curl -fsS --max-time 8 "$base/healthz" >"$artifact_dir/health" 2>/dev/null; then return; fi; sleep 2; done; docker logs --tail 60 "$app" >&2; return 1; }
wait_app
for route in / /login /onboarding /home/example /invite/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa /visit/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa; do
 curl -fsS -D "$artifact_dir/headers" "$base$route" >"$artifact_dir/index"
 python3 - "$artifact_dir" <<'PY'
from pathlib import Path
import sys
p=Path(sys.argv[1]);assert '<div id="root">' in (p/'index').read_text();assert 'no-store' in (p/'headers').read_text().lower()
PY
done
asset=$(python3 - "$artifact_dir/index" <<'PY'
import re,sys
print(re.search(r'src="(/assets/[^\"]+\.js)"',open(sys.argv[1]).read()).group(1))
PY
)
curl -fsS -D "$artifact_dir/asset-headers" "$base$asset" >/dev/null
[[ $(curl -sS -o /dev/null -w '%{http_code}' "$base/assets/missing.js") == 404 ]]
[[ $(curl -sS -o /dev/null -w '%{http_code}' "$base/api/me") == 401 ]]
[[ $(curl -sS -o /dev/null -w '%{http_code}' -X POST "$base/api/auth/dev") == 403 ]]
[[ $(curl -sS -o /dev/null -w '%{http_code}' "$base/studio") != 200 ]]
curl -fsS "$base/api/auth/config" >"$artifact_dir/auth"
curl -sS -D "$artifact_dir/oauth-headers" -o /dev/null -H 'Host: untrusted.example' -H 'X-Forwarded-Host: evil.example' -H 'X-Forwarded-Proto: http' "$base/oauth2/authorization/google"
docker cp "$app:/app/app.jar" "$artifact_dir/app.jar" >/dev/null
python3 - "$artifact_dir" <<'PY'
from pathlib import Path
import sys,json,zipfile,urllib.parse
p=Path(sys.argv[1]);assert 'immutable' in (p/'asset-headers').read_text().lower()
a=json.loads((p/'auth').read_text());assert a=={'googleEnabled':True,'devEnabled':False}
lines=(p/'oauth-headers').read_text().splitlines();location=next(l.split(':',1)[1].strip() for l in lines if l.lower().startswith('location:'))
query=urllib.parse.parse_qs(urllib.parse.urlsplit(location).query);assert query['redirect_uri']==['https://beta.example.org/login/oauth2/code/google']
with zipfile.ZipFile(p/'app.jar') as z:
 names=z.namelist();assert 'BOOT-INF/classes/static/index.html' in names
 assert not any('BOOT-INF/lib/h2-' in n for n in names)
 assert not any('.tools/' in n or '.env' in n for n in names)
 assert b'SPA delivery test fixture' not in z.read('BOOT-INF/classes/static/index.html')
PY
[[ $(docker inspect "$app" --format '{{.Config.User}}') == 10001:10001 ]]
[[ $(sql 'SELECT COUNT(*) FROM dothome.flyway_schema_history WHERE success=1') -ge 6 ]]
sql 'CREATE TABLE dothome.beta_persistence_probe(id INT PRIMARY KEY); INSERT INTO dothome.beta_persistence_probe VALUES (1)' >/dev/null
docker restart "$app" >/dev/null; wait_app
[[ $(sql 'SELECT COUNT(*) FROM dothome.beta_persistence_probe') == 1 ]]
docker stop "$database" >/dev/null
[[ $(curl -sS --max-time 15 -o "$artifact_dir/down" -w '%{http_code}' "$base/healthz") == 503 ]]
python3 - "$artifact_dir/down" <<'PY'
import json,sys
assert json.load(open(sys.argv[1]))=={'status':'DOWN'}
PY
docker start "$database" >/dev/null; wait_db; wait_app
[[ $(sql 'SELECT COUNT(*) FROM dothome.beta_persistence_probe') == 1 ]]
echo 'PASS: production Docker image, bundled SPA, cache/404, OAuth origin, API security, MySQL migrations, readiness failure/recovery, restart persistence'
