# 로컬 실행과 운영 준비

로컬 실행과 베타 운영 지침이다. [Railway 배포 상세 절차](DEPLOYMENT.md)에 Dashboard 메뉴·환경변수·Google Cloud·CI·백업을 정리했다. Docker/MySQL 실행은 CI에서 검증했으며 실제 Railway 공개 배포·Google E2E·복원은 아직 미검증이다.

## 로컬 저장

`bash scripts/server.sh dev`는 loopback에 바인딩하며 개발용 계정과 H2 파일 DB를 사용한다. 파일은 `apps/server/data/` 아래에 생성되고 Git ignore 대상이다. 이 디렉터리를 지우면 로컬 집과 계정 데이터가 사라진다. 새로 실행해도 기존 DB를 그대로 사용한다.

DB 파일은 서버가 사용하는 동안 복사하지 않는다. 로컬 백업은 Spring을 정상 종료한 후 전체 `data/` 디렉터리를 별도 날짜 폴더에 복사하고, 복원 시험은 새 작업 디렉터리에서 수행한다. 데이터 파일·쿠키·초대 토큰을 공유 저장소에 올리지 않는다.

## MySQL 8.4

1. Docker가 있는 환경에서 `.env.example`을 `.env`로 복사하고 로컬 DB 암호를 설정한다.
2. `docker compose up -d mysql`로 MySQL을 실행한다.
3. 애플리케이션 환경변수 `DB_URL`, `DB_USER`, `DB_PASSWORD`를 설정한다. Spring은 루트 `.env`를 자동으로 읽지 않는다.
4. 로컬 MySQL 검증은 `bash scripts/server.sh mysqlTest`로 별도 Testcontainers DB를 사용한다.
5. 실제 앱에서 MySQL을 사용할 때는 기본 프로필 또는 `google` 프로필을 선택한다. 개발 인증으로 로컬 MySQL만 시험하려면 위 DB 환경변수를 지정한 뒤 `dev`로 실행한다.

Flyway V1은 도메인 스키마, V2는 Spring Session 테이블, V3는 MySQL용 명시적 외래키, V4~V6는 외형·v0.6 도메인 및 제약을 추가한다. JPA는 `ddl-auto=validate`이며 스키마를 임의 재생성하지 않는다. 이미 적용한 마이그레이션은 수정하지 않고 새 버전을 추가한다.

## Google OIDC와 HTTPS

[Google 로그인 실행 지침](GOOGLE_LOGIN.md)에서 Cloud 프로젝트·Auth Platform Branding/Audience·Web Client·정확한 Redirect URI·테스트 사용자·환경변수 설정을 진행한다. `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`은 서버에만 저장한다. 프런트에 Client Secret 또는 Google 토큰을 저장하지 않는다.

로컬 파일 DB는 `APP_ORIGIN=http://127.0.0.1:5173 bash scripts/server.sh google`로 실행한다. 실제 운영 MySQL은 필수 DB/Google/PORT/APP_ORIGIN 환경변수와 google,prod 프로필로 JAR를 실행한다. 로컬 테스트의 dev와 google/prod는 동시에 활성화할 수 없다. 원격 preview는 local 또는 local,google만 사용하며 dev endpoint를 제공하지 않는다.

로컬 Spring은 loopback 바인딩과 `forward-headers-strategy=none`으로 전달 헤더를 무조건 신뢰하지 않는다. 콜백은 APP_ORIGIN으로 고정하며 운영·HTTPS 테스트 모두 해당 값을 정확한 외부 origin으로 설정한다. Vite/Nginx는 원래 Host를 유지하고 /oauth2·/login/oauth2·/api·/ws를 전달한다. Nginx 예시는 `infra/nginx.conf.example`에 있다. HTTPS의 Secure·HttpOnly·SameSite=Lax 쿠키, CSRF, WebSocket Origin 검사를 유지한다.

Google 인증 정보는 로컬 비공개 환경 파일에 설정되어 있고 이전 iPhone 사진에서 로그인 후 진입을 확인했다. 전체 가족 흐름의 실계정 E2E는 아직 미완료다. Mock 테스트와 실제 OAuth 성공은 별도로 기록한다. 초대 목적지 보존은 서버 세션·OAuth state와 함께 구현했으며, Google 로그인 뒤 원래 초대 화면에서 직접 입주를 수락해야 한다.

## 백업·복원과 오류 확인

MySQL 논리 백업은 `mysqldump --single-transaction --no-tablespaces`를 사용하고, DB 자격증명은 명령행 문자열에 넣지 말고 권한이 제한된 옵션 파일이나 비밀값 주입 방식을 사용한다. 백업에는 가구 데이터와 세션 데이터가 포함될 수 있으므로 접근을 제한하고 암호화한다.

복원은 빈 검증 DB에 먼저 수행한다. Flyway history, 계정·가구·초대 관계, 재고, 사건 건수와 앱 로그인·조회까지 확인한 뒤 운영 복원을 결정한다. 현재 이 복원 연습은 미수행이다.

기본 애플리케이션 로그는 표준 출력, 테스트 결과는 `apps/server/build/reports/tests/test/`에 남는다. 일반 입력 오류는 400, 인증은 401, 권한은 403, 버전/정책 충돌은 409, 만료 초대는 410이다. 로그에 OAuth secret·세션 쿠키·초대 원문 토큰을 남기지 않는다. 접근 로그에서 `/invite/:token` 및 `/api/invitations/:token`은 마스킹하거나 수집에서 제외한다.

## 모바일 온보딩 (2026-11-02)

[온보딩 구현·검증·캡처](ONBOARDING.md)에 A→B→C와 실제 Safari 확인 절차를 기록했다. 개발 서버의 /studio/onboarding은 저장되지 않는 시안이며 production 경로가 아니다. 실제 신규 사용자 흐름은 Google 로그인 후 /onboarding이다. 별도 DB 마이그레이션이나 신규 비밀값은 필요 없다. 임시 입력은 계정별 탭 세션에만 보관하고 성공/로그아웃 시 지운다.

## 외부 베타 운영

운영 Docker 이미지는 google,prod로 0.0.0.0:${PORT}에 바인딩하고 MySQL만 사용한다. 앱+정적 SPA의 단일 서비스·1 replica와 private MySQL 영구 볼륨을 사용한다. /healthz는 readiness와 DB 상태를 함께 검사한다. 변수 누락·dev/local 혼용·안전하지 않은 쿠키/스키마/로그 설정은 부팅 실패한다. DB 초기화나 로컬 H2 자동 이관은 하지 않는다.

운영 access log는 비활성화했다. Railway edge HTTP 로그의 토큰 마스킹/수집 제외는 별도 확인이 필요하며 앱 설정으로 완료했다고 간주하지 않는다. [배포 지침](DEPLOYMENT.md)의 미완료 점검표를 따른다.

## 미리보기 실행 파일 보존

`remote-preview.py start`는 bootJar 산출물을 `.tools/remote-preview/releases/server-<SHA256>.jar`로 복사해 읽기 전용으로 실행한다. `scripts/server.sh preview`는 시작 도구가 지정한 `DOT_HOME_PREVIEW_JAR`를 실행한다. 이미 실행 중인 기본 H2 미리보기에는 `python3 scripts/remote-preview.py upgrade`를 사용한다. 백엔드를 정상 종료한 뒤 DB와 설정을 백업하고 새 JAR를 연결한다. 기동 실패 시 이전 설정으로 다시 실행하고 DB 백업은 복구 검토용으로 남긴다. `python3 -m unittest discover -s scripts -p 'test_*.py'`로 실행 파일 보존, 백업과 설정 복구를 검사한다.


## 창밖의 지역 날씨

집 화면에 들어오면 브라우저가 필요에 따라 위치 권한을 요청한다. 허용하면 기기에서 위도·경도를 소수점 한 자리로 반올림하여 Open-Meteo에 직접 보낸다. 도트홈 서버, DB, 가족 동기화에는 위치를 보내지 않는다. 위치는 열린 화면의 메모리에서만 사용한다. 위치 공유는 브라우저의 사이트 권한에서 변경할 수 있다.

제공자의 지역 시간대, 일조 여부, 날씨 코드와 관측 시각을 사용한다. 열린 화면에서는 30분마다 갱신하고, 제공자가 실패하면 2시간 이내의 마지막 관측값을 표시한다. 해당 관측 시각은 보기 설정에서 확인할 수 있다. 권한이 없거나 관측이 더 오래되면 기기 시간에 맞춘 기본 하늘을 그린다. 외부에 공개하는 상업 서비스에서는 Open-Meteo의 요금제와 이용 조건을 확인한다. [Open-Meteo API 문서](https://open-meteo.com/en/docs)
