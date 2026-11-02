# 도트홈 — 웹게임 개발 전달서 v0.5

## v6.2 / v0.7 구현

공유 카탈로그 → Spaces/SpaceController와 nativeWorld/spaces/SpacePreview를 연결했다. SpaceSettings는 미리보기/취소 후 명시적 저장, design_version 잠금 비교, post-commit 이벤트를 사용한다. V8은 additive이며 legacy XY를 응답에서만 새 spawn으로 투영한다. [상세 구조와 호환성](V62_INTEGRATION.md).


작성일 / 최종 수정일: 2026-10-31 (Asia/Seoul). 상태: 진행 중 — 개발 명세 작성, 제품 구현 전. 분류: 개발 > 개발 도구 및 생산성. 태그: 바이브코딩, AI.

## 1. 목표와 범위

PC·태블릿·모바일 브라우저에서 URL로 접속하는 공동생활 웹게임을 Codex로 개발한다. 실제 참여한 가족과 반려동물을 도트 공간에서 보고, 얼굴 아이콘으로 관찰 대상을 바꾸며 말풍선·식료품·생활 사건을 공유한다.

가족 초대·인증·공유 저장을 제외한 단독 플레이 게임으로 축소하지 않는다. 최초 아트 검증 단계인 M0만 임시 데이터로 구현할 수 있고, 웹 MVP 완성은 M0~M3의 실제 연동과 검증을 기준으로 한다. 별도 점수·레벨·강제 퀘스트는 추가하지 않는다.

## 2. 기술·플랫폼

React·TypeScript·Vite·Phaser와 Java 21·Spring Boot·Spring Security·JPA, MySQL·Flyway를 사용한다. HTTP 통신은 REST, 실시간 장면 통신은 Spring WebSocket + STOMP와 @stomp/stompjs로 구현한다. 상세 선정과 국내 기업 사례는 TECH_STACK 문서에 있다.

이전 PWA·Capacitor 중심의 플랫폼 계획을 반응형 웹으로 대체한다. React·Phaser·Spring·MySQL은 웹에도 적합하므로 유지한다. WebSocket은 양방향 행동·상태 이벤트를 위한 선정이다. 네이티브 앱·앱스토어·PWA 설치는 현재 개발 범위에 없다.

## 3. 구조와 상태 경계

권장 폴더는 apps/web, apps/server, assets, docs다. 기존 프로젝트 구조가 있으면 먼저 확인한다. 웹의 URL 이동에는 React Router, 서버 데이터에는 TanStack Query를 사용한다. 서버는 identity·household·invitation·world·pantry·notification 등 기능별 패키지를 둔 단일 앱으로 시작한다.

| 상태 | 책임 | 규칙 |
|---|---|---|
| 인증 사용자 | 서버 세션 Principal | 요청 본문의 사용자 ID를 인증 근거로 사용하지 않음 |
| 내 구성원 | 계정의 Membership | 실제 가구 가입 관계 |
| 관찰 구성원 | React UI | 얼굴 선택으로 변경, 인증·조작 권한 불변 |
| 공유 장소·기분·음식 | 서버 DB | 작성자·출처·갱신·만료 시각 |
| 가상 행동·이동 목표 | 서버가 검증한 장면 상태 | 현실 위치·생활 사실과 분리 |
| 매 프레임 좌표 | Phaser | 보간·렌더링, React 매 프레임 상태 변경 금지 |
| 음량·배율·메뉴 | 클라이언트 설정 | localStorage 가능, 공유 기록의 원본은 아님 |

React↔Phaser는 타입이 정의된 이벤트·어댑터로 연결한다. 장면·라우트 변경 때 타이머·리스너·엔진 인스턴스를 해제한다. Phaser가 인증과 일반 DOM 폼까지 임의로 관리하지 않게 한다.

## 4. 첫 진입과 화면 흐름

1. URL 접속 → 로그인/시작 화면.
2. 대표가 집 이름과 본인 이름·외형만 설정한다. 생성 직후 가족 1명·반려동물 0마리다.
3. 집과 대표 가입을 한 트랜잭션으로 저장한다. 대표와 등록 반려동물만 등장한다.
4. 초대받은 사람은 링크에서 인증·수락·본인 프로필 연결을 마친 뒤 입주한다. 링크 열람만으로 가입시키지 않는다.
5. 실제 가입자의 얼굴 아이콘을 누르면 그 사람이 있는 집·직장·학교·밖을 관찰한다.
6. 관찰 변경과 내 공유 장소 변경은 별개다. '내 화면으로'로 본인 공간에 복귀한다.
7. 냉장고·가구·말풍선 상호작용으로 공동생활 정보를 조회·기록한다. 작성자는 로그인 계정이다.
8. 재접속하면 서버 데이터로 복구한다. 브라우저 종료는 가구 탈퇴가 아니다.

경로 초안: / 시작, /login 로그인, /onboarding 집 설정, /invite/:token 초대, /home/:householdId 게임, /settings 설정. 경로 ID는 권한 증명이 아니다. 초대 토큰이 분석 로그·Referer로 노출되지 않도록 설계한다.

## 5. 데이터 모델 초안

아래는 실행된 DDL이 아닌 설계다. API·동시성 검증에 맞춰 제약과 Flyway 마이그레이션을 작성한다.

| 모델 | 핵심 데이터 | 무결성 |
|---|---|---|
| User / ExternalIdentity | 계정, 제공자·subject | 제공자 식별 조합 고유 |
| Household | 집 이름, 소유자, revision | 실제 가족 수는 Membership에서 계산 |
| Membership | 가구·계정·역할·가입 상태·외형 | 중복 가입 방지, 미참여자 미표시 |
| Invitation | 토큰 해시·가구·만료·수락·취소 | 열람/수락 분리, 동시 수락 트랜잭션 |
| Pet | 가구·이름·종류·외형 | 계정 불필요, 0마리 지원 |
| SharedStatus | 구성원·장소·기분·음식·출처·시간 | 본인 수정, 오래된 상태 구분 |
| AvatarState | 장면·논리 위치·행동·시작 시각·버전 | 서버 검증, 매 프레임 DB 쓰기 금지 |
| PantryItem | 가구·품목·보관 위치·재고 방식·값·기한·버전 | 동시 수정 손실·음수 재고 방지 |
| HouseholdEvent / Notification | 사건 ID·가구·유형·작성자·시각·확인 | 중복 식별, 가구 접근 통제 |
| ShoppingItem / PetCareLog | 장보기·실제 돌봄 기록 | 추가 후보, 자율 연출로 완료 처리 금지 |

가입·공유 상태·재고는 DB에 저장한다. 애니메이션 프레임과 연결 상태는 별도로 관리한다. 중요한 변경은 트랜잭션 커밋 후 이벤트로 알린다. 가족 인원 상한은 두지 않으며 데모의 8명을 제한으로 가져오지 않는다.

## 6. REST 계약 초안

| 동작 | 경로 예시 | 조건 |
|---|---|---|
| 내 계정 | GET /api/me | 세션 사용자·가입 가구 |
| 집 생성 | POST /api/households | 집과 대표 가입 동시 저장 |
| 초대 생성 | POST /api/households/{id}/invitations | 대표 권한·만료·취소 |
| 초대 수락 | POST /api/invitations/{token}/accept | 인증·유효성·중복 가입·토큰 일회성을 원자적으로 검증 |
| 월드 초기/복구 | GET /api/households/{id}/world | 실제 가입자·반려동물·공유 상태·revision |
| 내 상태 | PATCH /api/households/{id}/me/status | 작성자를 인증에서 결정 |
| 식료품 | /api/households/{id}/pantry-items | 가구 범위·버전 충돌 |
| 사건·알림 | /api/households/{id}/events, /api/notifications | 저장·조회·확인; GET events는 REST 이력 조회 |

검증 오류·미인증·권한 없음·만료·버전 충돌·중복 요청 응답을 명시한다. Google Cloud OAuth Client 등록·redirect URI·비밀값 설정 전에는 개발용 인증을 별도 프로필로 제한하고 운영에서 활성화되지 않게 한다. 설정 없이 실제 OAuth 연결이 끝났다고 보고하지 않는다.

## 7. WebSocket 계약과 복구

endpoint 초안은 /ws, 입력 의도는 /app/households/{id}/actions, 상태 이벤트는 /topic/households/{id}/events, 개인 오류는 /user/queue/errors로 구분한다. 클라이언트의 /topic/** 직접 발행은 거부한다. Spring 버전에 맞는 설정과 보안 테스트를 작성한다.

| 방향 | 예시 종류 | 내용 |
|---|---|---|
| 클라이언트 → 서버 | MOVE_INTENT, INTERACT_INTENT | requestId·가구·목표; 행위자는 Principal에서 결정 |
| 서버 → 가구 | MEMBER_JOINED, STATUS_CHANGED, AVATAR_ACTION_CHANGED | eventId·revision·서버 시각·payload |
| 서버 → 가구 | PANTRY_CHANGED, LIFE_EVENT_CREATED | DB 커밋 후 통지·조회 캐시 갱신 |
| 서버 → 본인 | 입력 거절·권한 만료·재동기화 | 개인 오류 정보 |

REST는 가입·재고 등 지속 데이터의 명령 경로다. 동일한 재고 명령을 REST와 WebSocket에 중복 구현하지 않는다. WebSocket은 빠른 장면 입력 의도와 변경 알림을 담당한다.

서버는 가구 가입, 자기 캐릭터 여부, 장면, 이동 가능한 영역·속도·메시지 빈도를 검증한다. 가상 이동으로 현실 위치를 변경하지 않는다. 처음부터 고빈도 물리 서버를 만들지 않고 이동 목표·시작 시각을 보내 브라우저에서 보간하는 방식으로 검증한다.

heartbeat·backoff 재연결·중복 eventId 무시·revision 검증을 구현한다. 재연결 시 구독 후 이벤트를 잠시 버퍼링하면서 REST 스냅샷을 읽고, 스냅샷 이후 revision만 적용하는 등 구독/조회 사이 누락을 막는다. 불일치가 감지되면 최신 스냅샷으로 복구한다. simple broker를 영구 이벤트 로그로 취급하지 않는다.

다중 탭은 마지막으로 제어권을 받은 연결이 자기 캐릭터를 제어하는 안으로 검증한다. 로그아웃·세션 만료·가구 탈퇴·권한 회수 때 기존 소켓·구독·제어권도 회수한다. 가입 상태와 온라인 상태를 분리한다.

## 8. 인증·인가·운영

Spring Security OAuth2 Client로 Google OIDC 로그인 후 서비스 세션을 만든다. 동일 출처 HttpOnly·Secure 쿠키와 Spring Session JDBC를 기본 검토안으로 한다. OAuth state, CSRF, 허용 Origin 검증을 유지한다. REST뿐 아니라 STOMP CONNECT·SEND·SUBSCRIBE도 가구별로 인가한다. 인증됐다는 이유로 모든 가구 topic을 구독하게 하지 않는다.

동일 HTTPS 출처에서 정적 웹 파일과 /api·인증·/ws를 연결한다. Nginx의 WebSocket Upgrade·timeout과 heartbeat를 맞춘다. 비밀값은 환경변수로 관리하고 로컬·검증·운영 설정을 분리한다. 초기에는 단일 Spring 서버와 MySQL로 시작하며, 서버 확장 시 공유 이벤트·연결 라우팅을 함께 설계한다.

## 9. 자율 행동과 웹 입력

유휴 상태에서는 장소·성격·시간에 따른 규칙으로 행동한다. 예: 대기 → 이동 → 소파 휴식 → 반려동물 곁에 머물기. 직접 입력을 우선하고 유휴 시 자율 행동으로 복귀한다. 지속적인 LLM 호출은 필수가 아니다.

참여자가 오프라인이어도 캐릭터를 유지하고, 서버의 시간·행동 기준과 저장 상태로 장면을 재구성한다. 여러 관찰자가 임의로 실제 공유 상태를 생성하지 않게 한다. 숨김 탭의 렌더링을 줄이고 복귀 시 서버 상태로 동기화한다. 브라우저를 닫아도 JavaScript가 계속 실행된다고 가정하지 않는다.

PC는 클릭·키보드, 모바일은 탭 중심으로 조작한다. 입력 폼 포커스에서는 이동 키를 가로채지 않는다. 크기 변경·주소 직접 접근·새로고침·뒤로 가기·세션 만료를 처리한다. 사운드는 사용자 조작 후 재생하고 음소거를 제공한다.

## 10. 그래픽 제작 순서

M0에서 아트 시안과 대표 집 장면을 실제 엔진에 적용한 뒤 기능과 자산을 함께 확장한다. 이전 HTML의 그래픽은 목표가 아니다. 캐릭터·가구 비율, 타일, 충돌, 가림 순서, 초상화·말풍선 규격을 ART_DIRECTION에 기록한다.

집을 먼저 목표 품질로 만들고 직장·학교·밖에 같은 규칙을 적용한다. 외부 자산의 출처·사용 조건을 기록한다. 아트 검토 전에도 독립적인 인증·DB 작업은 가능하지만 미정 스타일로 모든 공간을 대량 제작하지 않는다.

## 11. 구현 단계

| 단계 | 결과 | 완료 조건 |
|---|---|---|
| M0 | 웹 기반·아트 시안·대표 집 | React/Phaser, 이동·가림·가구 접근·말풍선·반응형 입력; 임시 데이터 구분 |
| M1 | 실제 계정·집·초대·DB | 두 계정/브라우저, 수락 후 등장, 재접속 유지, 타 가구 접근 차단 |
| M2 | 공동 공간·얼굴 관찰·자율 행동 | 집·직장·학교·밖의 기본 장면, 인증 불변 관찰, 실시간·재연결·권한 회수 |
| M3 | 말풍선·식료품·수동 생활 사건·웹 내 알림 | 공유 저장·재고 충돌·중복 알림·브라우저 QA; 이 단계까지 웹 MVP |
| M4 | 선택 확장 | 사진·환영·꾸미기·Web Push·배송/IoT; 네이티브 앱은 별도 결정 |

퇴근·귀가·택배는 M3에서 사용자가 명시적으로 기록하는 방식으로 시작한다. 자동 조회·위치 감지를 구현했다고 표현하지 않는다. 알림은 웹 안의 표시와 사건 이력을 포함하고 닫힌 브라우저의 Web Push는 별도다.

## 12. Codex 작업과 보고

CODEX_START_PROMPT로 M0부터 시작한다. 각 단계는 실제 구현·실행·검사·실패 수정을 포함한다. 외부 설정이 없으면 미완료 항목을 명시하고 가능한 독립 작업은 진행한다. 문서 작성이나 모의 데이터만으로 실제 제품 완성을 주장하지 않는다.

보고 항목: 변경 파일, 실행 방법, 단계 완료 기준, 실제 검사 결과, 미검증, 필요한 외부 설정, 다음 작업. 자세한 체크리스트는 ACCEPTANCE를 따른다. 예시 명령을 실행한 명령으로 보고하지 않는다.

## 13. 현재 상태와 변경

현재 완료는 웹플랫폼 v0.5 명세·기술 선정·Codex 전달 자료다. 실제 웹게임·새 아트·서버·DB·인증·실시간 연동은 미구현이다. 이전 HTML은 기능 참고용이다.

v0.4에서 PWA/앱 확장 중심을 웹 브라우저로 변경하고 REST+WebSocket으로 재설계했다. 가족 참여·공유 저장을 유지하면서 웹 MVP와 완료 기준을 명시했다. 최종 아트·서비스명·초대 남용 방어·대표 이전·여러 가구·예산·외부 연동 설정은 아직 미정이다.

## 14. 공식 자료

- [토스증권 프런트 플랫폼](https://toss.im/career/job-detail?job_id=5130165003)
- [카카오페이 서버 환경](https://tech.kakaopay.com/post/happy-server-engineer/)
- [Phaser React·TypeScript](https://github.com/phaserjs/template-react-ts)
- [Spring Boot 요구사항](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring STOMP](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/overview.html)
- [Spring WebSocket 보안](https://docs.spring.io/spring-security/reference/servlet/integrations/websocket.html)
- [OpenAI 공식 AGENTS.md 문서](https://learn.chatgpt.com/docs/agent-configuration/agents-md)

## v0.6 증분 구현 결과

[P1/P2/P3 구현 및 검증](V06.md). 기능별 ObjectNotes/FamilyCalendar/Housewarming 서버 파일과 React 컴포넌트로 분리했다. 기존 REST 식별자·가족 가입·LifeEvent NOTE는 유지했다. Flyway V4~V6 추가, 신규 DTO/인가/버전/공개 방문 컨텍스트를 도입했다. POST/수정 후 커밋 이벤트와 REST 재동기화를 연결했다. /visit/:token은 Google OAuth state에 묶인 내부 반환 경로이며 가족 가입이 아니다. 방문자는 별도 공개 실내 아트와 방명록만 사용한다.

## 입장 후 가족·반려동물 관리

CreateHouse는 name/memberName/appearance만 받는다. Plan DTO와 계획 인원 PATCH를 제거했다. accept의 READ_COMMITTED/가구·초대 행 잠금은 유지하고 capacity 검사만 제거했다. 서로 다른 토큰은 모두 입주 가능하고 같은 토큰 경쟁은 한 계정만 성공한다. 동일 계정의 서로 다른 토큰 동시 수락도 기존 Membership를 재사용한다.

POST pets는 대표만 한 마리 등록하고 PATCH pets/{petId}는 이름/종류를 바꾼다. 기존 Pet와 appearance는 유지한다. 등록 외형은 현재 기본 cream만 받으며 수정 요청은 외형을 변경하지 않는다. world 응답에 목록을 포함하고 PETS_CHANGED 후 재조회한다. Phaser는 이름 변경 시 label을 즉시 갱신하고 종류 변경 시 텍스처를 다시 선택한다.

V7은 legacy planned_people에 DEFAULT 1을 설정한다. 앱/JPA에는 필드가 없으며 기존 데이터는 건드리지 않는다. 이전 버전의 JPA 스키마 검증과 운영 rollback 지원을 위해 물리 삭제를 분리했다. 이전 앱으로 되돌리면 예전 인원 정책이 재적용되므로 초대 쓰기는 중지하고 조정해야 한다. 이후 지원 종료 시 별도 migration으로 컬럼을 삭제한다.

초대 남용 방어는 가족 수 제한과 별개다. 현재 대표 권한/CSRF/안전한 토큰/클라이언트 중복 클릭 방지를 유지한다. 다중 재배포에도 유지되는 사용자·가구별 발급 rate limit은 후속 작업이며, 프로세스 메모리만의 임시 상한을 제품 인원 제한처럼 도입하지 않는다.
