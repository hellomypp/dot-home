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
5. 실제 앱에서 MySQL을 사용할 때는 기본 프로필 또는 `kakao` 프로필을 선택한다. 개발 인증으로 로컬 MySQL만 시험하려면 위 DB 환경변수를 지정한 뒤 `dev`로 실행한다.

Flyway V1은 도메인 스키마, V2는 Spring Session 테이블, V3는 MySQL용 명시적 외래키를 추가한다. JPA는 `ddl-auto=validate`이며 스키마를 임의 재생성하지 않는다. 이미 적용한 마이그레이션은 수정하지 않고 새 버전을 추가한다.

## 카카오와 HTTPS

- 카카오 개발자 앱의 웹 도메인과 `/login/oauth2/code/kakao` redirect URI를 설정한다.
- `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, `APP_ORIGIN=https://실제도메인`, DB 환경변수를 외부 비밀값 저장소 또는 서버 환경에 제공한다.
- 서버: `java -jar apps/server/build/libs/dot-home-server-0.1.0.jar --spring.profiles.active=kakao`.
- 프런트: 빌드 환경에 `VITE_KAKAO_ENABLED=true`를 설정한다. `VITE_DEV_AUTH`는 운영에서 설정하지 않는다.
- `dev`와 `kakao`/`prod`를 같이 켜면 서버가 시작을 거부한다. 운영에서는 개발 인증 endpoint가 없다.
- `infra/nginx.conf.example`에 도메인·TLS 경로·정적 빌드 경로를 적용한다. `/ws` Upgrade와 timeout을 함께 설정한다.
- 쿠키는 기본 HttpOnly·Secure·SameSite=Lax다. CSRF와 WebSocket Origin 검사는 유지한다.
- OAuth 복귀 후 초대 링크를 다시 열어 수락하는 흐름까지 검증해야 한다. 로그인 전 초대 URL의 자동 복귀 UX는 아직 완성되지 않았다.

## 백업·복원과 오류 확인

MySQL 논리 백업은 `mysqldump --single-transaction --no-tablespaces`를 사용하고, DB 자격증명은 명령행 문자열에 넣지 말고 권한이 제한된 옵션 파일이나 비밀값 주입 방식을 사용한다. 백업에는 가구 데이터와 세션 데이터가 포함될 수 있으므로 접근을 제한하고 암호화한다.

복원은 빈 검증 DB에 먼저 수행한다. Flyway history, 계정·가구·초대 관계, 재고, 사건 건수와 앱 로그인·조회까지 확인한 뒤 운영 복원을 결정한다. 현재 이 복원 연습은 미수행이다.

기본 애플리케이션 로그는 표준 출력, 테스트 결과는 `apps/server/build/reports/tests/test/`에 남는다. 일반 입력 오류는 400, 인증은 401, 권한은 403, 버전/정책 충돌은 409, 만료 초대는 410이다. 로그에 OAuth secret·세션 쿠키·초대 원문 토큰을 남기지 않는다. 접근 로그에서 `/invite/:token` 및 `/api/invitations/:token`은 마스킹하거나 수집에서 제외한다.
