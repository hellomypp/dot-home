# 도트홈 v0.5 개발 시작판

움직이는 집 장면과 서버에 저장되는 공동생활 기능을 구현한 첫 개발 버전입니다. 원본 명세는 보존했습니다. **운영 출시 완료나 M0~M3 전체 검증 완료를 뜻하지 않습니다.**

## 바로 실행

프로젝트 루트 `dot-home-codex`에서 실행합니다. Node 24.15+ / pnpm 11.25.0 / Java 21이 필요합니다. 이 컴퓨터에서는 `scripts/pnpm.sh`가 Codex 번들 Node·pnpm을 찾아 사용합니다.

```sh
bash scripts/pnpm.sh install --frozen-lockfile

# 터미널 1: 로컬 개발용 인증 + 파일 DB + Spring 서버
bash scripts/server.sh dev

# 터미널 2: React / Phaser
bash scripts/dev.sh
```

- 게임 시작: <http://127.0.0.1:5173/>
- 로그인 없이 아트·이동 시안 보기: <http://127.0.0.1:5173/studio>
- API: `127.0.0.1:8080`. Vite가 `/api`, `/oauth2`, `/login/oauth2`, `/ws`를 프록시합니다.
- 로컬 접속은 `127.0.0.1`로 통일합니다. `localhost`를 쓰려면 서버 실행 시 `APP_ORIGIN=http://localhost:5173`을 지정하세요.

개발용 로그인에서 영문·숫자·`_`·`-`로 2~64자의 계정 키와 표시 이름을 입력합니다. 같은 계정 키로 재접속하면 같은 계정으로 복구됩니다. 실제 사용자 비밀번호를 입력하는 방식이 아니며, `dev` 프로필은 외부 공개용이 아닙니다.

집을 만들 때 본인 포함 계획 인원과 반려동물 수를 정합니다. 다른 사람은 링크를 열고 별도로 인증한 뒤 초대를 수락해야 나타납니다. 다른 계정을 검증하려면 별도 브라우저 프로필이나 시크릿 창을 사용하세요. 모바일에서는 오른쪽 위 계정 버튼 → ‘초대 관리’로 들어갑니다.

## 구현한 기능

- React 폼·메뉴·얼굴 목록, Phaser 장면·클릭/키보드 이동·가구 충돌·가림·말풍선, 두 가지 아트 색감, 모바일 관찰 카메라.
- 본인과 관찰 대상 분리, 집·직장·학교·밖의 기본 공간, 유휴 걷기·대기와 등록 반려동물 연출.
- Spring Security 세션과 CSRF, 집 생성, 계획 인원 변경, 해시로 저장하는 72시간 초대, 취소·만료·동시 수락 제어.
- 서버가 작성자를 결정하는 기분·음식·생각, 만료·해제, 식료품 수량과 사용 이력, 버전 충돌, 수동 귀가·퇴근·택배·메모 사건.
- STOMP 가구별 SEND/SUBSCRIBE 인가, 재연결과 구독 준비 확인 후 스냅샷 복구, 중복 사건 처리, 다중 탭 제어권, 로그아웃·가입 권한 회수 시 소켓 정리.
- Flyway 마이그레이션 3개, Gradle Wrapper와 pnpm lockfile, CI 설정, MySQL Compose 및 HTTPS 프록시 예시.

실제 집 데이터는 브라우저 localStorage에 저장하지 않습니다. 개발 프로필은 독립 실행을 위한 H2 파일 DB(`apps/server/data/dothome.mv.db`)를 사용하며, 기본 서버 설정과 배포 대상은 MySQL 8.4입니다. H2 검사 결과를 MySQL 검사 결과로 간주하지 않습니다.

## 검증 명령

```sh
bash scripts/pnpm.sh test
bash scripts/pnpm.sh build
bash scripts/server.sh test bootJar

# 두 개발 서버가 실행 중일 때: 별도 QA 계정으로 REST 흐름 검증
python3 scripts/smoke.py
# Spring을 종료했다 다시 실행한 후: 기존 QA 데이터 복구 확인
python3 scripts/smoke.py --verify

# Docker가 실행되는 환경에서 실제 MySQL 통합 검사
bash scripts/server.sh mysqlTest
```

현재 확인한 결과는 프런트 테스트 6개, 서버 통합 테스트 14개, 웹 빌드·서버 JAR 빌드, 격리된 계정의 HTTP 시나리오 및 서버 재시작 후 저장 복구 통과입니다. 브라우저에서는 집 장면·모바일 390px 레이아웃·말풍선 폼을 확인했습니다. 자세한 범위와 미검증 항목은 [검증 기록](docs/VERIFICATION.md)에 있습니다.

## 다음 단계

1. 집 시안의 색감·비율 검토와 앉기 등 행동 스프라이트 확장.
2. Docker/MySQL에서 `mysqlTest` 실행, 실제 두 브라우저의 전체 가입→초대→관찰→공유 E2E 및 세션 만료 검사.
3. 카카오 앱 등록·리다이렉트 URI·비밀값 설정, `kakao` 프로필과 프런트 `VITE_KAKAO_ENABLED=true` 적용 후 OAuth 검증.
4. HTTPS 배포와 백업/복원 연습, Safari·Android·iPhone/iPad 실기기 및 성능 검사.

최종 아트, OAuth, MySQL 실운영, HTTPS URL은 아직 확정·검증되지 않았습니다. 실행 중인 로컬 서버는 공개 웹사이트 배포가 아닙니다.

## 문서와 코드

| 경로 | 내용 |
| --- | --- |
| `apps/web/src/App.tsx` | 시작·집 설정·초대·공동생활 화면 |
| `apps/web/src/game/` | 픽셀 아트·장면·충돌·길 찾기 |
| `apps/web/src/lib/` | REST·CSRF·실시간 복구 |
| `apps/server/` | Java 서버·마이그레이션·통합 테스트 |
| [API](docs/API.md) | 실제 구현 계약 |
| [아트 규격](docs/ASSETS.md) | 시안·출처·충돌·스프라이트 규격 |
| [버전](docs/VERSIONS.md) | 공식 호환성 확인과 고정 버전 |
| [검증 기록](docs/VERIFICATION.md) | 통과 검사와 남은 기준 |
| [운영 준비](docs/OPERATIONS.md) | MySQL·OAuth·HTTPS·백업 절차 |
