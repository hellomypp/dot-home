# 로컬 실행과 운영 준비

이 문서는 실행 방법과 준비 사항이다. 외부 서버 배포·HTTPS·MySQL 복원은 아직 검증하지 않았다.

## 로컬 저장

`bash scripts/server.sh dev`는 loopback에 바인딩하며 개발용 계정과 H2 파일 DB를 사용한다. 파일은 `apps/server/data/` 아래에 생성되고 Git ignore 대상이다. 이 디렉터리를 지우면 로컬 집과 계정 데이터가 사라진다. 새로 실행해도 기존 DB를 그대로 사용한다.

DB 파일은 서버가 사용하는 동안 복사하지 않는다. 로컬 백업은 Spring을 정상 종료한 후 전체 `data/` 디렉터리를 별도 날짜 폴더에 복사하고, 복원 시험은 새 작업 디렉터리에서 수행한다. 데이터 파일·쿠키·초대 토큰을 공유 저장소에 올리지 않는다.

## MySQL 8.4

1. Docker가 있는 환경에서 `.env.example`을 `.env`로 복사하고 로컬 DB 암호를 설정한다.
2. `docker compose up -d mysql`로 MySQL을 실행한다.
3. 애플리케이션 환경변수 `DB_URL`, `DB_USER`, `DB_PASSWORD`를 설정한다. Spring은 루트 `.env`를 자동으로 읽지 않는다.
4. 로컬 MySQL 검증은 `bash scripts/server.sh mysqlTest`로 별도 Testcontainers DB를 사용한다.
5. 실제 앱에서 MySQL을 사용할 때는 기본 프로필 또는 `google` 프로필을 선택한다. 개발 인증으로 로컬 MySQL만 시험하려면 위 DB 환경변수를 지정한 뒤 `dev`로 실행한다.

Flyway V1은 도메인 스키마, V2는 Spring Session 테이블, V3는 MySQL용 명시적 외래키를 추가한다. JPA는 `ddl-auto=validate`이며 스키마를 임의 재생성하지 않는다. 이미 적용한 마이그레이션은 수정하지 않고 새 버전을 추가한다.

## Google OIDC와 HTTPS

[Google 로그인 실행 지침](GOOGLE_LOGIN.md)에서 Cloud 프로젝트·Auth Platform Branding/Audience·Web Client·정확한 Redirect URI·테스트 사용자·환경변수 설정을 진행한다. `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`은 서버에만 저장한다. 프런트에 Client Secret 또는 Google 토큰을 저장하지 않는다.

로컬 파일 DB는 `APP_ORIGIN=http://127.0.0.1:5173 bash scripts/server.sh google`로 실행한다. 실제 운영 MySQL은 DB 환경변수와 google 프로필로 JAR를 실행한다. 로컬 테스트의 dev와 google/prod는 동시에 활성화할 수 없다. 원격 preview는 local 또는 local,google만 사용하며 dev endpoint를 제공하지 않는다.

Spring은 loopback 바인딩과 `forward-headers-strategy=none`으로 전달 헤더를 무조건 신뢰하지 않는다. 콜백은 APP_ORIGIN으로 고정하며 운영·HTTPS 테스트 모두 해당 값을 정확한 외부 origin으로 설정한다. Vite/Nginx는 원래 Host를 유지하고 /oauth2·/login/oauth2·/api·/ws를 전달한다. Nginx 예시는 `infra/nginx.conf.example`에 있다. HTTPS의 Secure·HttpOnly·SameSite=Lax 쿠키, CSRF, WebSocket Origin 검사를 유지한다.

실제 Google Cloud 정보가 없어 아직 Google 계정으로 로그인하거나 실제 콜백을 검증하지 않았다. Mock 테스트와 실제 OAuth 성공은 별도로 기록한다. 초대 목적지 보존은 서버 세션·OAuth state와 함께 구현했으며, Google 로그인 뒤 원래 초대 화면에서 직접 입주를 수락해야 한다.

## 백업·복원과 오류 확인

MySQL 논리 백업은 `mysqldump --single-transaction --no-tablespaces`를 사용하고, DB 자격증명은 명령행 문자열에 넣지 말고 권한이 제한된 옵션 파일이나 비밀값 주입 방식을 사용한다. 백업에는 가구 데이터와 세션 데이터가 포함될 수 있으므로 접근을 제한하고 암호화한다.

복원은 빈 검증 DB에 먼저 수행한다. Flyway history, 계정·가구·초대 관계, 재고, 사건 건수와 앱 로그인·조회까지 확인한 뒤 운영 복원을 결정한다. 현재 이 복원 연습은 미수행이다.

기본 애플리케이션 로그는 표준 출력, 테스트 결과는 `apps/server/build/reports/tests/test/`에 남는다. 일반 입력 오류는 400, 인증은 401, 권한은 403, 버전/정책 충돌은 409, 만료 초대는 410이다. 로그에 OAuth secret·세션 쿠키·초대 원문 토큰을 남기지 않는다. 접근 로그에서 `/invite/:token` 및 `/api/invitations/:token`은 마스킹하거나 수집에서 제외한다.
