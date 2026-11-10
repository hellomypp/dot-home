# 도트홈 개발판

비공개 미리보기에서는 자동 위치·귀가와 이전 집의 내 기록을 사용할 수 있다. 브라우저 알림은 VAPID 설정을 완료한 환경에서 기기별로 켤 수 있다. 구현과 실제 검증 범위는 [v0.9 작업 기록](docs/V09_WORKPLAN.md)과 [검증 기록](docs/VERIFICATION.md)을 확인한다.

## 로컬 작업: v6.2 그래픽과 v0.7 공유 집 설정

정원집·햇살 아파트·벽돌 로프트, 제공된 캐릭터 3종, 대표의 공유 가구 외형 설정을 구현했다. 기존 Google/초대/가족·반려동물 정책은 유지한다. [구현·데이터 호환·검증 기록](docs/V62_INTEGRATION.md)을 확인한다. 이번 변경은 외부 배포 및 GitHub push 전 상태다.


움직이는 집 장면과 서버에 저장되는 공동생활 기능을 구현한 첫 개발 버전입니다. 원본 명세는 보존했습니다. **운영 출시 완료나 M0~M3 전체 검증 완료를 뜻하지 않습니다.**

## Google 로그인과 실행

공식 로그인은 **Google 계정(OAuth 2.0 Authorization Code + OpenID Connect)** 하나입니다. 신규 사용자는 집 생성 또는 받은 초대 링크의 명시적 수락으로 시작합니다. 로그인만으로 다른 가구에 가입하지 않습니다. 기존 Google 계정은 같은 내부 계정·캐릭터로 복구합니다. 이메일이 같아도 개발 계정과 자동 병합하지 않습니다.

Google Cloud에서 Web application Client를 생성하고 승인된 callback을 등록한 뒤, 서버 전용 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`을 설정하세요. [Google 설정 및 실제 검증 상태](docs/GOOGLE_LOGIN.md)에 단계별 절차가 있습니다. 기존 iPhone 사진에서 Google 로그인 후 진입은 확인했으며, 전체 가족 흐름의 실계정 E2E는 별도 검증이 필요합니다.

```sh
cd ~/Developer/dot-home
bash scripts/pnpm.sh install --frozen-lockfile
# .tools/google.env에 서버용 Google 인증 정보를 입력한 뒤:
APP_ORIGIN=http://127.0.0.1:5173 bash scripts/server.sh google
# 다른 터미널
bash scripts/dev.sh
```

- 시작: <http://127.0.0.1:5173/>
- 아트 시안: <http://127.0.0.1:5173/studio>
- API는 loopback 8080, Vite가 REST/OAuth/WebSocket을 프록시합니다.
- Node 24.15+ / pnpm 11.25.0 / Java 21. 이 Mac에서는 번들 런타임 fallback도 제공합니다.
- 개발용 임의 계정 키 로그인이 필요한 자동/로컬 테스트만 `bash scripts/server.sh dev`로 실행합니다. 이 로그인은 loopback 호스트에서만 허용되고 운영/원격 Google 로그인과 함께 사용할 수 없습니다.
- iPhone 미리보기는 `python3 scripts/remote-preview.py start|stop|status|upgrade`를 사용합니다. [원격 실행](docs/IPHONE_PREVIEW.md) 참조. Google 설정 전에는 시안과 설정 대기 화면만 제공하며 원격 개발 계정 로그인을 허용하지 않습니다.

## 모바일 온보딩

스토리 시작 → 집 정보 → 캐릭터 꾸미기 → 입주 완료를 구현했습니다. 집 이름과 내 캐릭터를 설정하면 입주할 수 있습니다. 가족과 반려동물은 입주 후 추가합니다. 온보딩은 세션 임시 입력 복원과 작은 계정 메뉴를 제공합니다. [구현 결과·테스트·화면 캡처·iPhone 확인 방법](docs/ONBOARDING.md). 개발 서버의 `/studio/onboarding`에서 저장 없이 시안을 확인할 수 있습니다.

## 구현한 기능

- React 폼·메뉴·얼굴 목록, Phaser 장면·클릭/키보드 이동·가구 충돌·가림·말풍선, 두 가지 아트 색감, 모바일 관찰 카메라.
- 본인과 관찰 대상 분리, 집·직장·학교·밖의 기본 공간, 유휴 걷기·대기와 등록 반려동물 연출.
- Spring Security 세션과 CSRF, 집 생성, 입장 후 반려동물 등록·수정, 해시로 저장하는 72시간 초대, 취소·만료·동시 수락 제어.
- 서버가 작성자를 결정하는 기분·음식·생각, 만료·해제, 식료품 수량과 사용 이력, 버전 충돌, 수동 귀가·퇴근·택배·메모 사건.
- STOMP 가구별 SEND/SUBSCRIBE 인가, 재연결과 구독 준비 확인 후 스냅샷 복구, 중복 사건 처리, 다중 탭 제어권, 로그아웃·가입 권한 회수 시 소켓 정리.
- Flyway 마이그레이션 7개, Gradle Wrapper와 pnpm lockfile, CI 설정, MySQL Compose 및 HTTPS 프록시 예시.

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

현재 확인한 결과는 프런트 테스트 42개, 서버 테스트 67개, 웹 빌드·서버 JAR 빌드, 격리된 계정의 HTTP 시나리오 및 서버 재시작 후 저장 복구 통과입니다. 브라우저에서는 집 장면·모바일 390px 레이아웃·말풍선 폼을 확인했습니다. 자세한 범위와 미검증 항목은 [검증 기록](docs/VERIFICATION.md)에 있습니다.

## 다음 단계

1. 집 시안의 색감·비율 검토와 앉기 등 행동 스프라이트 확장.
2. CI의 MySQL 8.4 통합 검사 22건은 통과했으며 실제 두 브라우저의 전체 가입→초대→관찰→공유 E2E 및 세션 만료 검사는 별도 수행.
3. Google Cloud Client·콜백·테스트 사용자 설정 후 실제 Google 계정 E2E 검증.
4. HTTPS 배포와 백업/복원 연습, Safari·Android·iPhone/iPad 실기기 및 성능 검사.

최종 아트, 실제 Google 인증, MySQL 실운영, HTTPS URL은 아직 확정·검증되지 않았습니다. 실행 중인 로컬 서버는 공개 웹사이트 배포가 아닙니다.

## 문서와 코드

| 경로 | 내용 |
| --- | --- |
| `apps/web/src/App.tsx` | 시작·집 설정·초대·공동생활 화면 |
| `apps/web/src/game/` | 픽셀 아트·장면·충돌·길 찾기 |
| `apps/web/src/lib/` | REST·CSRF·실시간 복구 |
| `apps/server/` | Java 서버·마이그레이션·통합 테스트 |
| [Google 로그인](docs/GOOGLE_LOGIN.md) | 인증 구조·Cloud 설정·초대 복귀·테스트 |
| [API](docs/API.md) | 실제 구현 계약 |
| [아트 규격](docs/ASSETS.md) | 시안·출처·충돌·스프라이트 규격 |
| [버전](docs/VERSIONS.md) | 공식 호환성 확인과 고정 버전 |
| [검증 기록](docs/VERIFICATION.md) | 통과 검사와 남은 기준 |
| [운영 준비](docs/OPERATIONS.md) | MySQL·OAuth·HTTPS·백업 절차 |

## v0.6 가족 생활 기능

냉장고 쪽지·떼어 읽기·가족 보관함, 가족 공유 캘린더, 가족 가입과 분리된 집들이·방명록을 실제 서버 DB/게임에 연결했습니다. [사용 방법·API·마이그레이션·검증·남은 실기기 확인](docs/V06.md). 기존 V1~V3와 가족 데이터는 유지하며 새 V4~V6가 추가됩니다.

## 가족·친구 외부 베타 배포

[Railway 배포 지침](docs/DEPLOYMENT.md)에 계정 로그인부터 private MySQL 8.4 영구 볼륨, Google Cloud callback, 단일 Docker 앱, Wait for CI, 백업·복원을 설명했습니다. 루트 Dockerfile은 React 빌드 결과를 Spring Boot JAR에 포함하며 운영 google,prod 프로필은 필수 환경변수와 MySQL을 검증합니다. /healthz 및 SPA deep link·캐시 정책을 제공합니다. CI는 실제 Docker 이미지 기동, MySQL migration, 재시작 데이터 유지와 DB 장애 복구를 검사합니다.

**Railway 리소스와 공개 URL은 아직 생성하지 않았습니다.** 웹 로그인 및 비용 승인 후 배포하고 실제 Google 인증·두 계정·실기기 검증을 수행해야 합니다. Mac/Tailscale 미리보기는 독립 외부 배포가 아닙니다.

## 닉네임 변경

게임 상단 오른쪽 내 계정 → 닉네임 입력 → ‘닉네임 저장’으로 해당 집의 내 표시 이름을 바꿀 수 있습니다. 가족 목록·캐릭터 이름표와 다른 가족 화면에 반영되고 재접속 후에도 유지됩니다. Google 계정 이름은 변경하지 않습니다.

## 집 생성과 가족·반려동물

집 이름과 내 캐릭터만 설정하면 1명·반려동물 0마리로 시작합니다. 가족은 초대를 수락할 때 추가되며 사전 인원 제한은 없습니다. 게임의 ‘반려동물 등록 · 관리’에서 대표가 한 마리씩 등록하고 이름·종류를 수정합니다. 기존 가족·반려동물 데이터는 유지됩니다.
