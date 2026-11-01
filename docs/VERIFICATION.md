# 2026-11-01 구현·검증 기록

원본 `ACCEPTANCE.md`는 목표 체크리스트로 보존한다. 이 문서만 실제 수행 결과를 기록한다. M0~M3 웹 MVP 전체 완료를 선언하지 않는다.

## 환경

- macOS / arm64, Amazon Corretto Java 21.0.3.
- Node 24.22.1, pnpm 11.25.0, Vite 8.3.5, Phaser 4.2.1, Spring Boot 4.1.2, Gradle 9.2.1.
- 서버 테스트: H2 메모리 DB. 실행·재시작 확인: H2 파일 DB. MySQL 및 Docker 데몬은 이 작업 환경에서 준비되지 않았다.
- UI 확인: Codex 내장 Chromium. 데스크톱 폭 1280px 및 390×844px 뷰포트. 브라우저 엔진의 정확한 버전과 실기기 검증은 미기록·미수행.

## 실행해 통과한 검사

| 검사 | 실제 결과 |
| --- | --- |
| `bash scripts/pnpm.sh install --frozen-lockfile --offline --store-dir .pnpm-store` | 잠금 파일 설치 재현 통과 |
| `bash scripts/pnpm.sh build` | TypeScript 검사 + 운영 웹 빌드 통과 |
| `bash scripts/pnpm.sh test` | 2파일, 6개 통과 |
| `bash scripts/server.sh test bootJar` | 2클래스, 14개 통과; 실행 JAR 생성 |
| `python3 scripts/smoke.py` | 독립 쿠키 계정 3개의 실제 REST 흐름 통과 |
| `python3 scripts/smoke.py --verify` | 서버 프로세스 교체 후 가입·0마리·재고·사건 복구 통과 |
| 브라우저 장면 | React + Phaser 렌더, 시안 표시, 데스크톱/모바일 레이아웃, 말풍선 입력·반영 확인 |
| 브라우저 재연결 | 재시작 후 기존 게임 화면의 ‘연결됨’ 확인 |

프런트 6개: 가구·경계 차단, 소파 우회 경로, 가구 접근점 보정, 가구 그림과 바닥 충돌선 구분, 중복/과거 revision 무시, 공유 상태 만료.

서버 10개: 반려동물 0마리·미참여자 숨김·수락 후 등장, 가구 및 대표 권한, 취소·만료, 마지막 자리 동시 수락, 동일 초대 동시 사용, 재고 버전·음수 방지, 사건 중복·서버 작성자, 상태 만료, 잘못된 이동·재고 독립성, 실제 HTTP 쿠키 세션과 CSRF.

실제 WebSocket 4개: 커밋 후 사건 전달·타 가구 구독 차단, topic 직접 발행·타인 ID 주입 거절, 마지막 탭 제어권·로그아웃 소켓 종료, 재연결 스냅샷 복구·가입 권한 회수 종료.

HTTP smoke: 개발 로그인 A/B/C → 집 생성(계획 3명, 반려동물 0마리) → 링크 열람 시 1명 유지 → B 수락 후 2명 → C 조회 403 → A 품목 등록/B 사용 → A의 오래된 버전 수정 409 → B의 동일 사건 재전송 한 건 유지. 격리된 QA 계정과 가구만 사용했다.

## 발견해 수정한 문제

- 작은 화면의 말풍선이 카메라 경계를 벗어나던 문제: 좌우 경계 안으로 위치 보정.
- 모바일에서 왼쪽 초대 메뉴가 숨겨지던 문제: 계정 설정 안에 초대 관리 추가.
- 중복 종류 가구 텍스처 제거: 동일 텍스처 재사용으로 변경.
- Spring Boot 4의 JDBC 세션 자동 구성: 전용 session-jdbc starter 사용.
- 초대 동시 수락의 영속성 컨텍스트 캐시: 가구 잠금 후 초대 행을 다시 읽어 같은 토큰이 두 번 소비되지 않게 수정.
- 단순 브로커에서 STOMP receipt를 기다리는 검사 실패: 별도 `SYNC_READY` 응답과 수신 순서 보장 후 스냅샷을 조회하도록 수정.
- 프록시된 서비스의 필드를 직접 읽던 초기 동기화 오류: 필요한 서비스를 직접 주입.
- 네트워크 복구 전에 CSRF 조회가 실패하는 경우: WebSocket의 재시도 경로를 유지하도록 처리.

## 단계별 현재 범위

| 단계 | 현재 구현 | 아직 남음 |
| --- | --- | --- |
| M0 | 두 색감, 원본 자산, 집 장면, 이동·가림·충돌·말풍선, 입력·반응형 기초 | 최종 스타일 검토, 전용 앉기 프레임, 실기기 터치·DPR·다중 말풍선 배치·성능 측정 |
| M1 | 개발 인증, 세션, 가입·초대·권한·서버 저장, 재시작 복구 | 실제 Google OIDC, MySQL 통합 실행, 두 브라우저 전체 E2E |
| M2 | 기본 4공간, 얼굴 관찰, 자율 걷기, STOMP 인가·복구·다중 탭·권한 회수 | 장면의 정확한 서버 시간 기반 행동 재구성, 이동 경로/속도 검증 강화, 자연 세션 만료 장기검사 |
| M3 | 상태 공유·만료, 식료품·사용 이력, 수동 사건·웹 내 이력 | 실제 다중 기기 검증, 알림 읽음/설정, 정확한 개수 외 잔량 방식, HTTPS·운영 검증 |

## 미검증·제한

- `mysqlTest`는 Testcontainers용 소스와 CI를 준비했으나 Docker 부재로 실행하지 않았다. H2 테스트 통과는 MySQL 마이그레이션·잠금·동시성의 보증이 아니다.
- 브라우저 검사는 내장 Chromium과 반응형 뷰포트 기준이다. 두 브라우저에서 전체 시나리오를 자동 E2E로 통과했다고 주장하지 않는다. `test:e2e` 실행기에 대한 시나리오 파일도 후속 작업이다.
- 실제 OAuth 클라이언트 ID/secret, 운영 도메인·TLS 인증서·MySQL 서버가 필요하다. 기본 쿠키 보안 설정과 프록시 예시는 있으나 HTTPS·OAuth 배포 검증은 수행하지 않았다.
- 캐릭터 좌표는 이동 목표이며 클라이언트에서 경로를 계산해 보간한다. 서버는 공간·좌표 경계·가구 밑면·빈도·작성자를 검사하지만 완전한 서버 경로 시뮬레이션이나 치트 방지 완료가 아니다.
- 자율 연출은 시간 구간·구성원 ID를 바탕으로 브라우저에서 재구성한다. 브라우저를 닫은 동안 실제 돌봄·재고·가전 변경을 실행하지 않는다.
- 다중 탭은 마지막 가구 구독 연결이 제어권을 갖는다. 이전 탭의 입력은 거절하고 소켓을 닫는다. 자동 재연결과 제어권 안내 UX를 더 다듬어야 한다.
- 탈퇴·대표 이전의 제품 정책과 관리 UI, 장보기·돌봄·IoT·배송 API·Web Push는 이번 첫 구현에 포함하지 않았다.
- 프레임·메모리·로딩 성능 수치는 측정하지 않았다. 설정한 30fps는 측정 결과가 아니다.
- 운영 백업·복원 절차는 문서만 제공하며 실제 복원 연습을 통과하지 않았다.

원본 자료는 그대로 남겼다. 새 구현과 검증 문서는 `README.md`, `API.md`, `ASSETS.md`, `VERSIONS.md`, `OPERATIONS.md`에서 이어간다.


## Google 로그인 전환 검증 (2026-11-01)

공식 정책은 Google 하나로 변경했다. 이전 기록의 6/14개는 첫 구현 기준이다. 이번 기준은 프런트 13개, 서버 30개다. `bash scripts/pnpm.sh test`, `bash scripts/pnpm.sh build`, `bash scripts/server.sh test bootJar`를 실행한다. 최종 결과는 실행 보고와 테스트 XML을 함께 확인한다.

새 검사는 검증된 OIDC sub의 내부 계정 매핑, 같은 sub 재사용·서로 다른 sub 구분·이메일 비병합, 이름 기본값/길이, Principal.getName 내부 ID, JDBC 세션용 직렬화, 고정 callback·최소 scope·OAuth state, 초대 목적지의 세션 보존·외부 반환 URL 차단·수락 전 미표시, 신규 집 생성·재방문 캐릭터 복구, 실패·취소, 로그아웃, Google 운영의 dev 차단과 원격 호스트의 개발 로그인 거절을 포함한다.

기존 프런트·H2·WebSocket 회귀 검사를 유지한다. MySQL의 기존 동시성 실패 두 건은 [DH-001](KNOWN_ISSUES.md)로 추적하며 이번 인증 검사 통과와 구분한다. 실제 Google Client ID/Secret이 없어 Google의 실제 토큰 교환과 Safari/Chrome 계정 E2E는 미검증이다.

최종 실행 결과: 프런트 13/13, 서버 30/30(기존 WebSocket 4 포함), Vite/TypeScript 빌드와 bootJar 통과. HTTPS 미리보기에서 /studio 200, /api/me 401, /api/auth/config의 googleEnabled=false/devEnabled=false(외부 인증 정보 미설정), Secure·HttpOnly 쿠키와 개발 로그인 거절을 확인했다. 내장 Chromium에서도 Google 버튼 하나와 설정 대기 안내, 개발 폼 미표시를 확인했다. 이는 실제 Google 인증 성공 검증을 의미하지 않는다.


### 실제 Google 인증 정보 적용 후 진행

사용자가 제공한 인증 정보를 .tools/google.env(0600, Git ignore)에 저장하고 local,google 서버를 재시작했다. /api/auth/config에서 googleEnabled=true/devEnabled=false, 실제 Google authorization redirect에서 HTTPS callback·openid/profile/email·state·nonce를 확인했다. 브라우저에서 Google 계정 선택 화면까지 연결됐다. 사용자의 계정 선택·동의 및 실제 콜백 완료는 아직 대기 중이며, 실제 Google 로그인 성공으로 판정하지 않는다. 비밀값·일회성 인증 URL은 문서나 소스에 기록하지 않았다.

### iPhone HTTPS 전용 오류 수정

사용자 사진에서 로그인 후 HTTP 127.0.0.1로 이동하는 오류를 확인했다. 실제 HTTPS callback 오류 응답을 조회해 Location이 http://127.0.0.1:8080/login?error=google인 것을 재현했다. 상대 sendRedirect가 내부 요청의 scheme/host로 확장되던 문제여서 성공·실패·초대 복귀 모두 검증한 APP_ORIGIN 기반 절대 URL로 변경했다. 전달 헤더 전체 신뢰나 Safari HTTPS 전용 해제는 사용하지 않았다. 프록시의 HTTP loopback 요청과 위조 Host에도 HTTPS를 유지하는 회귀 검사 추가 후 서버 31개 통과 및 bootJar 빌드 성공. 서버 재시작 후 실제 실패 응답의 HTTPS Location도 확인했다. 사용자 계정으로 성공 콜백까지 완료하는 E2E는 재확인 대기 중이다.

### 집 생성 전 로그아웃 누락 수정

사용자 iPhone 사진에서 /onboarding에는 게임 화면의 계정 메뉴가 없어 로그아웃할 수 없음을 확인했다. 집 생성·초대 화면에 현재 계정과 명시적 로그아웃 버튼을 추가했다. CSRF가 있는 POST 로그아웃 후 쿼리를 취소·초기화하고 /login으로 복귀한다. 집을 만들지 않은 Google 사용자도 재로그인할 수 있도록 회귀 테스트를 추가했다. 프런트 14개 통과 및 TypeScript/Vite 빌드 통과.


사용자의 iPhone 사진에서 Google 로그인 후 실제 프로필 이름이 표시된 집 생성 화면 진입을 확인했다. 사용자 보고 기반의 로그인·콜백 성공 확인이며, 초대 복귀·가구 생성·공유·재로그인의 실제 Google 계정 전체 E2E 완료와 구분한다.


## 2026-11-02: MySQL 초대 동시성 수정 검증

초대 수락 트랜잭션에 READ_COMMITTED를 적용해 가구 잠금을 기다린 후 수행하는 구성원 조회·인원 검사가 최신 커밋을 읽도록 수정했다. 로컬 서버 31개·bootJar 통과. 실제 GitHub CI 41353158782에서 기존 MySQL 실패 2건을 포함한 mysqlTest 및 서버·프런트 작업 전체가 success로 완료됐다. 테스트를 제외하거나 완화하지 않았다.

## 2026-11-02: 모바일 온보딩 A→B→C

프런트 24/24, 서버 32/32(WebSocket 포함), TypeScript/Vite 및 bootJar 통과. 새 외형과 여러 반려동물의 DB 저장·HTTP 생성→world 재조회·미인증 API 401을 검증했다. 기존 Google·가족 초대·동시성 테스트를 유지했다. 375·390·430·440·768·1280px의 A/B/C 캡처에서 가로 넘침 없음, 주요 CTA 높이 52px을 확인했다. [상세 검증·미검증 사항과 스크린샷](ONBOARDING.md).

이번 UI의 실제 iPhone Safari, 실제 Google 계정으로 새 집 생성→게임→재로그인 E2E, Safari 소프트 키보드·주소창 검증은 아직 미수행이다. 내장 브라우저 캡처와 실기기를 혼동하지 않는다. MySQL 검사는 push 후 실제 GitHub Actions 결과와 구분해 기록한다.

코드 커밋 d9ef1d2의 실제 [GitHub Actions 41359992314](https://github.com/hellomypp/dot-home/actions/runs/41359992314)에서 web/server 모두 success를 확인했다. server 명령 `test mysqlTest bootJar`가 성공했으며 MySQL 초대 동시성 테스트와 신규 외형·여러 반려동물·HTTP 생성 검사를 제외하지 않았다. HTTPS 미리보기에서 최신 온보딩 소스와 Google 활성/dev 비활성도 확인했다.

## 2026-11-02: v0.6 사물 쪽지·가족 캘린더·집들이

프런트 33건, 서버 48건(H2·실제 HTTP/STOMP·Google mock 포함), TypeScript/Vite 및 bootJar 빌드 통과. 신규 도메인/상태·버전·동시 떼기/수정/방명록 재전송, 공개 방문 권한, 작성자 위조 400·CSRF 403·방문자 가족 API/STOMP 차단을 검사했다. 실제 REST smoke에서 두 가족+방문자의 생성·읽기·보관·취소·인원 불변과 가족 API 5개 차단을 확인했다.

격리된 5174→8081 메모리 DB에서 실제 로컬 세션을 사용해 모바일 쪽지 작성, 캘린더 등록·조회, 대표 방문 링크 발급, 로그아웃/새 방문자 로그인/원래 방문 목적지 복귀/명시 수락/방명록 저장을 UI로 확인했다. [상세 결과와 화면](V06.md). 실제 iPhone Safari·실제 Google 계정 전체 E2E·운영 복원은 미검증이다. MySQL 8.4 결과는 GitHub Actions 실행 후 별도로 기록한다.

코드 98ee7a7의 [GitHub Actions 41362627544](https://github.com/hellomypp/dot-home/actions/runs/41362627544)에서 web/server 모두 success, 실제 MySQL 8.4의 기존 11건+신규 11건 통합 검사 및 bootJar 성공을 확인했다. 프런트 집들이 반환 경로의 끝 줄바꿈 거부를 추가한 뒤 로컬 프런트 33건/서버 48건과 빌드를 재확인했다.

기존 로컬 DB를 서버 정상 종료 후 비공개 백업하고 실제 HTTPS 미리보기를 재시작했다. Flyway V4~V6 적용 성공(현재 v6), /studio 200, Google 활성/dev 비활성을 확인했다. 기존 데이터 테이블을 초기화하지 않았다. QA 메모리 DB와 실제 미리보기 DB는 별개다.
