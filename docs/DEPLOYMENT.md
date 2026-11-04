# Railway 외부 베타 배포

## v6.2 변경의 배포 전 조건

이번 작업은 push/배포하지 않는다. V8 additive 스키마와 native 좌표·v2 외형을 추가했다. 프로덕션 DB 백업/복구 시험, MySQL 8.4 테스트, Docker 이미지 빌드 및 실기기 확인 후 별도 배포한다. Docker 두 빌드 단계에 공유 space-catalog.json을 포함하고 Gradle spaceCatalogue 속성으로 컨테이너 경로를 지정한다. 기존 JAR로의 단순 롤백은 새 좌표 표시를 보장하지 않는다. [호환성 기록](V62_INTEGRATION.md).


2026-11-02 기준. **배포용 코드와 실제 Docker/MySQL 검증은 완료했지만 Railway 리소스·공개 URL·배포 도메인의 Google 인증은 아직 없다.** Railway 웹 로그인 및 비용 승인이 필요하다. Mac/Tailscale 미리보기와 독립적인 환경이며 로컬 H2 데이터는 이관하지 않는다.

## 구조와 저장소 산출물

`인터넷 HTTPS → Railway TLS 종료 → dot-home-app (Spring Boot + React 정적 파일 + REST + /ws) → private MySQL 8.4 + 영구 볼륨`.

앱은 **1 replica**다. SimpleBroker와 소켓 제어권이 메모리 기반이므로 여러 replica로 늘리지 않는다. 별도 Vite 서버·Redis·프런트 호스팅은 필요 없다. REST와 wss는 같은 Origin이다.

루트 Dockerfile은 Node 24.22.1/pnpm 11.25.0의 frozen lockfile 빌드, Java 21/기존 Gradle Wrapper의 bootJar, Java 21 JRE 런타임으로 구성한다. `-PwebDist`로 SPA를 JAR에 포함하고 `-PproductionImage`에서는 H2 라이브러리도 제외한다. 최종 비루트 UID/GID는 10001이다. `.dockerignore`가 비밀값·로컬 DB·도구·캐시를 제외한다. 비밀값은 빌드 인수가 아니라 실행 환경변수다.

신규 Railway 서비스에는 폐기된 `railway.json`을 추가하지 않았다. Railway 공식 문서는 신규 서비스의 Config as Code 사용을 금지하고 2026-12-01 읽기 중단을 안내한다. 현재는 아래 Dashboard 설정을 사용한다. 향후 IaC는 실제 리소스 생성 후 `railway config pull`로 가져와 검토한다. 새 `.railway/railway.ts`에 기존 리소스를 누락하면 삭제 계획이 될 수 있으므로 임의 `config apply`를 실행하지 않는다. [현재 IaC](https://docs.railway.com/infrastructure-as-code), [이전 설정 폐기](https://docs.railway.com/config-as-code/reference).

## 먼저 필요한 사용자 작업

1. [Railway Dashboard](https://railway.com/dashboard)에 로그인한다. GitHub 로그인이 필요하면 직접 인증한다. GitHub CLI 로그인은 Railway 웹 로그인과 별개다.
2. Workspace의 Billing에서 요금제·월 사용 예산·알림을 확인한다. 유료 리소스 생성과 결제는 사용자의 명시적 승인 후 진행한다. Trial도 만료·사용 한도가 있으므로 영구 무료 환경으로 간주하지 않는다.
3. GitHub 연동에서 필요한 저장소만 `hellomypp/dot-home`에 허용한다. OAuth 비밀값을 GitHub나 채팅에 붙여 넣지 않는다.

## 프로젝트와 MySQL 생성 (비용 승인 후)

1. Dashboard → New Project → Empty Project. 프로젝트 이름 `dot-home-beta`, 환경은 하나로 시작한다.
2. 프로젝트의 + New → Docker Image에서 **mysql:8.4.7**을 선택하고 서비스 이름을 `dot-home-mysql`로 지정한다. MySQL 템플릿의 최신 major가 8.4인지 추측하지 않는다. 이미 새로운 major로 초기화한 볼륨을 8.4에 재사용하지 않는다.
3. DB 서비스 Variables에 다음을 설정한다. 비밀번호 두 개는 서로 다른 충분히 긴 무작위 값으로 생성하고 비밀 관리 도구에 보관한다.

| 변수 | 값 |
| --- | --- |
| MYSQL_DATABASE | dothome |
| MYSQL_USER | dothome_app |
| MYSQL_PASSWORD | 앱 전용 DB 비밀번호 |
| MYSQL_ROOT_PASSWORD | 별도 root 비밀번호 |

4. DB 서비스 → Settings/Volumes에서 영구 Volume을 만들고 **/var/lib/mysql**에 연결한다. 앱과 DB를 같은 환경·가까운 동일 region에 둔다. replica 1개. DB에는 Public Domain/TCP Proxy를 생성하지 않는다.
5. 배포가 정상화되고 private domain과 DB 연결 준비가 확인될 때까지 기다린다. DB 서비스 삭제·볼륨 삭제·초기화는 실행하지 않는다.

이 절차는 공식 MySQL Docker 이미지 변수(`MYSQL_DATABASE` 등)를 사용하는 명시적 구성이다. Railway MySQL 템플릿의 `MYSQLDATABASE` 등 별칭과 혼동하지 않는다. [Railway MySQL](https://docs.railway.com/databases/mysql), [Private networking](https://docs.railway.com/networking/private-networking).

## 애플리케이션 생성과 변수

1. + New → GitHub Repo → `hellomypp/dot-home`. 서비스 이름 `dot-home-app`.
2. Settings → Source에서 branch `main`, Root Directory는 저장소 루트. 루트 Dockerfile 자동 감지를 확인한다. 별도 build/start command나 프런트 디렉터리로 덮어쓰지 않는다. [Dockerfile 감지](https://docs.railway.com/builds/dockerfiles).
3. Settings → Deploy에서 replica **1**, Healthcheck Path **/healthz**, timeout 180초를 설정한다. 초기 앱 메모리는 1 GB 정도부터 지표를 보고 조정한다. 앱에는 영구 볼륨이 필요 없다.
4. Settings → Networking → Public Networking → Generate Domain, target port **8080**. 발급받은 HTTPS origin을 아래 APP_ORIGIN에 넣는다. 생성 직후 변수 미설정 상태의 배포 실패는 정상이며, 필수 변수를 모두 설정한 뒤 다시 배포한다.
5. Variables에서 다음을 설정한다. 서비스 참조는 Add Reference로 실제 이름에 맞춰 생성한다.

| 변수 | 값 |
| --- | --- |
| SPRING_PROFILES_ACTIVE | google,prod |
| DOT_HOME_DEPLOYMENT | beta (이미지 기본값) |
| PORT | 8080 |
| APP_ORIGIN | https://발급된도메인 (끝 / 없이) |
| GOOGLE_CLIENT_ID | Google Web application Client ID |
| GOOGLE_CLIENT_SECRET | 서버 전용 Secret |
| DB_URL | 아래 JDBC URL |
| DB_USER | `${{dot-home-mysql.MYSQL_USER}}` |
| DB_PASSWORD | `${{dot-home-mysql.MYSQL_PASSWORD}}` |

```text
jdbc:mysql://${{dot-home-mysql.RAILWAY_PRIVATE_DOMAIN}}:3306/${{dot-home-mysql.MYSQL_DATABASE}}?serverTimezone=UTC&allowPublicKeyRetrieval=true&connectTimeout=10000&socketTimeout=30000
```

DB_URL에 비밀번호를 넣지 않는다. 위 `allowPublicKeyRetrieval`은 private network의 MySQL 8 인증용이다. DB public proxy를 만들지 않는다. Secret을 `VITE_` 변수·Docker ARG·로그에 넣지 않는다. 계정 변수는 가능한 경우 Railway의 sealed variable 기능을 사용한다.

6. Deploy에서 기동 및 `/healthz` 통과를 확인한다. 필수 변수 누락, HTTP origin, dev/local/test/kakao 프로필, 비 MySQL DB, Secure 쿠키 해제, JPA 재생성, access log 또는 DEBUG 설정은 부팅 검증에서 거절한다. Spring Session JDBC와 Flyway V1~V6는 유지하고 V7을 추가했다. V7은 legacy planned_people의 기본값만 설정하며 기존 행을 변경하지 않는다.

## Google Cloud Console

1. [Google Cloud Console](https://console.cloud.google.com/)에서 기존 OAuth 프로젝트를 선택한다.
2. Google Auth Platform → Branding: 도트홈 이름·지원 이메일을 확인한다.
3. Audience → External, Testing을 사용한다면 Test users에 가족·친구의 실제 Google 이메일을 추가한다.
4. Data Access → `openid`, `profile`, `email`만 사용한다.
5. Clients → 기존 Web application client → Authorized redirect URIs → **https://발급된도메인/login/oauth2/code/google**를 정확히 추가하고 저장한다. 기존 로컬/tailnet callback은 필요하면 유지한다.
6. 서버 Authorization Code Flow이므로 JavaScript SDK용 Authorized JavaScript origins는 이 구현의 필수 항목이 아니다. 입력할 경우 정확한 HTTPS origin만 사용한다.
7. APP_ORIGIN과 callback을 일치시키고 실제 기기에서 계정 선택 → 동의 → 도트홈 복귀까지 확인한다. Google 정책이 제공 도메인을 거부하면 우회하지 않는다. 소유 도메인을 Railway Settings → Networking → Custom Domain에 추가하고 안내된 DNS를 설정해 TLS 준비를 확인한다. Google에서 요구하는 도메인 소유 검증 및 승인 도메인 설정 후 해당 HTTPS callback으로 교체한다.

Google sub 기반 계정 식별, 서버 세션 12h, 명시적인 가족/방문 초대 수락을 유지한다. 로컬 H2와 새 클라우드 MySQL은 별개라 기존 로컬 캐릭터가 클라우드에 자동 등장하지 않는다.

## HTTP·세션·SPA·로그 보안

- 공개 GET은 알려진 SPA 경로(`/`, `/login`, `/onboarding`, `/home/{id}`, `/invite/{token}`, `/visit/{token}`, `/settings`)와 정적 asset, 인증 시작·CSRF/config·health로 한정한다. `/api/**`, `/ws`, OAuth callback은 SPA fallback하지 않는다. 없는 asset은 404다. `/studio`는 운영에서 제공하지 않는다.
- index는 no-store, Vite 해시 asset은 1년 immutable. 오류 응답에는 내부 예외를 넣지 않는다.
- 세션 Secure/HttpOnly/SameSite=Lax, CSRF, HSTS, no-referrer 및 기본 Security 헤더를 유지한다.
- TLS는 Railway에서 종료한다. Spring은 `forward-headers-strategy=none`, callback은 검증된 APP_ORIGIN으로 고정한다. 임의 Host/forwarded 헤더로 callback을 바꿀 수 없다. WS Origin도 APP_ORIGIN과 일치해야 한다.
- 앱 Tomcat 접근 로그를 끄고 Spring Web/Security는 WARN이다. 초대 토큰·OAuth code/state를 앱 접근 로그에 기록하지 않는다. 외부 분석/오류 수집 도구는 추가하지 않았다.
- **Railway 자체 edge HTTP 로그는 앱 로그와 별개다.** 공식 문서에는 path 필드가 있으며 수집 전 마스킹/제외 설정은 확인하지 못했다. Dashboard Observability의 HTTP Logs에서 일회성 가짜 초대 경로로 실제 수집 범위를 확인하고 Railway 지원에 해당 경로 및 OAuth query의 수집 제외/마스킹 가능 여부를 확인한다. UI 검색 필터는 수집 제외가 아니다. 플랫폼 측 토큰 로그 정책을 확인하기 전 실제 가족 초대를 배포 검증 완료로 처리하지 않는다. 로그/프로젝트 접근자는 최소화하고 캡처·공유에 인증 URL을 넣지 않는다. [HTTP 로그](https://docs.railway.com/observability/logs).
- `/healthz`는 앱 readiness와 실제 DB 연결 유효성을 모두 확인한다. 정상 200 `{"status":"UP"}`, 장애 503 `{"status":"DOWN"}`만 반환한다. Railway healthcheck는 배포 전환 검사이므로 상시 장애 감시와 별개다.

## GitHub Actions와 자동 배포

GitHub Actions `Verify`는 web 테스트/빌드 → server의 test+mysqlTest+bootJar → 실제 Docker 이미지 빌드 및 MySQL 8.4 컨테이너 검증을 수행한다. container job은 두 job 성공 이후 실행한다. main push에도 실행된다.

1. GitHub 저장소 Settings → Rules → Rulesets 또는 Branches에서 main 보호 규칙을 만들고 PR을 요구한다. Required status checks는 실제 실행 이름 **web, server, container**를 선택한다. 우회 허용 범위를 검토한다. 보호 규칙 적용 여부는 최종 보고와 구분한다.
2. Railway 앱 Settings → Source/Deploy의 **Wait for CI**를 켠다. 필요 시 Railway GitHub App 권한 갱신을 직접 승인한다. main 연결 및 자동 배포 활성화를 확인한다. [Wait for CI](https://docs.railway.com/deployments/github-autodeploys).
3. PR의 세 검사를 통과한 변경만 main에 병합한다. main에서 재실행한 Actions 성공 후 Railway가 배포하고 healthcheck 성공 시 동일 URL의 트래픽을 전환하는지 확인한다.
4. 배포 실패는 Deployments에서 원인을 확인한다. 이전 정상 배포가 계속 제공되는지 확인하고 필요하면 해당 배포의 메뉴 → Rollback을 사용한다. DB/볼륨을 삭제하지 않는다. 최초 배포 실패에는 이전 정상 배포가 없다.
5. Flyway 적용은 앱 rollback과 별개다. V1~V6를 변경하지 않고 V7을 추가했으며 향후 schema 변경은 이전 앱과 호환되는 추가 방식으로 먼저 배포한다. 파괴적인 migration을 앱 rollback으로 되돌릴 수 없다.

## 백업과 빈 DB 복원 시험

DB 서비스 → Backups에서 수동 backup을 생성하고 Scheduled Backups(일별 등)를 활성화한다. 비용·retention을 화면에서 확인한다. Native Restore는 같은 서비스의 볼륨을 교체할 수 있으므로 실제 베타 DB에서 연습하지 않는다. [볼륨 백업](https://docs.railway.com/volumes/backups).

논리 복원 연습은 별도의 **빈 private 검증 DB**와 별도 볼륨에서만 수행한다. 추가 리소스 비용 승인이 필요하다. 아래는 실행 예시이며 현재 수행하지 않았다. Railway CLI 로그인/프로젝트 연결 및 SSH 키 등록이 필요하다. [SSH 명령](https://docs.railway.com/cli/ssh).

```sh
# 저장소 밖의 암호화된 디스크에 저장. 실제 경로를 직접 지정한다.
umask 077
DOT_HOME_BACKUP_PATH=/Volumes/EncryptedBackup/dothome-beta.sql
railway ssh --service dot-home-mysql -- sh -lc \
 'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --single-transaction --quick --no-tablespaces --skip-add-drop-table --set-gtid-purged=OFF -u"$MYSQL_USER" "$MYSQL_DATABASE"' > "$DOT_HOME_BACKUP_PATH"
# 위와 동일한 MySQL 8.4.7 이미지로 만든 새 빈 DB에만 복원한다.
railway ssh --service dot-home-mysql-restorecheck -- sh -lc \
 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"' < "$DOT_HOME_BACKUP_PATH"
```

SSH 명령이 실패하면 빈/불완전 dump를 정상 백업으로 취급하지 않는다. dump에 DROP TABLE을 넣지 않아 기존 테이블이 있으면 복원 오류로 중단한다. Flyway history와 Account/Household/Membership/Pet/ObjectNote/Calendar/Guestbook의 건수·관계를 비교한다. 별도 앱을 검증 DB에 연결해 JPA validate·조회·초대 상태를 확인한다. 기존 로그인 세션은 개인 데이터로 취급하고 외부 공유하지 않는다. 베타 앱의 DB_URL을 실수로 검증 DB로 변경하지 않는다. 복원 연습 결과 기록 후 추가 리소스의 보관/삭제는 사용자와 결정한다.

## 배포 승인 전 실제 점검표

다음은 **클라우드에서 아직 미수행**이다. 가짜 OAuth 설정의 CI와 실제 Google E2E는 별개다.

- [ ] HTTPS 공개 URL, 인증서, `/healthz` 200, HTTP→HTTPS 전환
- [ ] `/login`, `/onboarding`, `/home/{id}`, `/invite/{token}`, `/visit/{token}` 직접 접속/새로고침, missing asset 404
- [ ] Google 취소/재시도, 신규 집/캐릭터/반려동물 생성, 재로그인 복구
- [ ] 다른 Google 테스트 계정의 초대 → 로그인 → 원래 경로 → 명시 수락, 수락 전 미표시
- [ ] wss `/ws`, 세션/CONNECT CSRF, 타 가구 SEND/SUBSCRIBE 403, 미인증 API 401, dev 로그인 거절
- [ ] 두 계정 쪽지 붙이기/떼기/보관, 캘린더 변경 동기화, 집들이 권한/방명록·가족 인원 불변
- [ ] 백그라운드 복귀, 네트워크 단절 및 서버 재배포 후 재연결·snapshot, 다중 탭 제어권
- [ ] 로그아웃 세션/소켓 종료, 만료·취소 초대 거절, 타 가구 API 차단
- [ ] 동일 이미지 재배포·서버 재시작 후 기존 집/가족/쪽지/일정/방명록 유지
- [ ] iPhone Safari / Android Chrome / PC Chrome / iPad Safari에서 위 흐름과 모바일 키보드·스크롤
- [ ] 플랫폼 edge 로그 토큰 처리 확인, 백업 활성화 및 빈 DB 복원 시험

## 비용과 중지

2026-11-02 공식 요금 기준 Hobby는 월 최소 $5이며 포함 크레딧을 넘는 사용량은 추가된다. RAM은 약 $10/GB·월이므로 앱+DB 평균 합계 1.5 GB라면 RAM만 약 $15/월에 CPU·디스크·트래픽·백업을 더한다. 이는 예시 계산이며 **월 $5 고정 요금이나 총액 보장이 아니다**. Workspace Usage에서 첫 주 사용량으로 월 예상치를 확인하고 예산 알림을 설정한다. [공식 요금](https://docs.railway.com/pricing), [Trial 한도](https://docs.railway.com/pricing/free-trial).

앱 중지는 서비스의 현재 배포 Stop/Remove Deployment 메뉴를 확인해 실행한다. DB/Volume 삭제는 데이터 삭제이므로 중지 수단으로 사용하지 않는다. 실행 compute를 멈춰도 볼륨·backup 보관 비용이 남을 수 있다. 재시작은 동일 변수·동일 DB 볼륨으로 Redeploy한다. 완전 종료 시 먼저 검증한 백업과 보관 정책을 합의한다.

## V7 배포·rollback 주의

DB를 백업한 뒤 V7을 적용한다. planned_people은 새 JPA/DTO/UI/정책에서 제거했지만 이전 바이너리의 스키마 호환을 위해 컬럼과 기존 값은 유지했다. 물리 삭제는 이전 버전 지원 종료 후 별도 migration으로 한다. 이전 앱으로 rollback하면 예전 인원 정책이 돌아오므로 새 초대 쓰기를 중지하고 호환성을 확인한다. 구형 브라우저의 POST body/계획 PATCH는 더 이상 지원하지 않으므로 새로고침을 안내한다. 실 배포 DB는 아직 없고 미리보기 파일 DB와 별개다.


## 집 삭제 활성화

`HOUSEHOLD_DELETION_ENABLED` 기본값은 false다. 기능을 켜면 집 대표가 7일 유예를 요청할 수 있고, 워커가 기한이 지난 해당 집의 행을 영구 삭제한다. 계정과 다른 집은 유지한다. 운영에서 활성화하기 전에 빈 MySQL 8.4 검증 DB에서 V24·삭제·복원을 실행한다. 계정 삭제 및 결제 보존 운영은 별개다.

### 복원에서 삭제 기록 재적용

1. 전체 백업과 별개로 최신 `household_deletion_log`의 데이터를 보관한다. MySQL에서는 `mysqldump --single-transaction --no-create-info --skip-triggers --skip-add-locks --no-tablespaces --set-gtid-purged=OFF --insert-ignore … household_deletion_log`로 데이터만 내보낼 수 있다. 계정 암호는 명령줄에 넣지 않는다. [MySQL 8.4 mysqldump](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html).
2. 별도 빈 DB에 전체 백업을 복원하고 Flyway V24 이상을 적용한다. 복원 DB에 최신 삭제 로그를 병합하고, 애플리케이션에 연결하기 전에 복원 DB 전용 `RESTORE_DB_URL`, `RESTORE_DB_USER`, `RESTORE_DB_PASSWORD`를 설정한다.
3. 조회 모드로 정리 대상 수를 확인한 후 적용한다. 두 번째 조회가 0건인지 확인하고, 보존할 집에서 로그인을 비롯한 핵심 동작을 검증한다.

```bash
bash scripts/server.sh replayHouseholdDeletions --args=--plan
bash scripts/server.sh replayHouseholdDeletions --args=--apply
bash scripts/server.sh replayHouseholdDeletions --args=--plan
```

`--apply`는 복원된 집 중 삭제 로그가 있는 집만 트랜잭션으로 정리한다. 삭제 로그 자체와 계정, 나머지 집은 보존하며 재실행이 가능하다. H2 복사본에서 검증했다. 실제 MySQL 백업 파일로 재현하는 단계는 운영 검증에 남아 있다.
