# 도트홈 구현 API 계약

개발 기준: 2026-11-01. 서버는 `/api`, STOMP endpoint `/ws`. 동일 출처 쿠키 세션. 모든 ID는 JSON 문자열, 시각은 ISO UTC. 공식 인증은 `google` 프로필의 Google OIDC. `dev`는 loopback 로컬 테스트에서만 허용하고 google/prod와 함께 시작할 수 없다.

## 인증

- `GET /api/auth/csrf` → `{token, headerName:"X-CSRF-TOKEN", parameterName:"_csrf"}`. 세션 쿠키를 유지하고 변경 요청마다 이 헤더를 전송한다. 로그인/로그아웃 뒤 재조회한다.
- `POST /api/auth/dev` body `{accountKey:"alice",name:"다은"}` → `GET /api/me`와 같은 응답. 계정 키는 로컬 검증 전용이며 같은 키는 같은 계정으로 복구한다.
- `GET /api/me` → `{user:{id,name},memberships:[{householdId,householdName,memberId,role:"OWNER"|"MEMBER"}]}`. 미인증 401.
- `POST /api/logout` → 204. CSRF 필수.
- Google 시작 `GET /oauth2/authorization/google`; 콜백 `/login/oauth2/code/google`. openid/profile/email만 요청한다.
- `GET /api/auth/config` → `{googleEnabled,devEnabled}`. 공개 설정 조회이며 인증 정보·토큰을 포함하지 않는다. UI는 서버 설정과 loopback 여부가 모두 맞을 때만 개발 폼을 표시한다.
- 가족 초대/집들이 복귀: `/oauth2/authorization/google?next=/invite/{43자 URL-safe 토큰}`. `/visit/{43자 토큰}`도 허용한다. 목적지는 OAuth state에 묶인 서버 세션에 저장하고 Google에 전달하지 않는다. 외부 URL·쿼리·fragment를 반환 경로로 허용하지 않는다.
- 성공: 원래 초대 화면 → 사용자의 명시적 수락. 목적지가 없으면 기존 가구 또는 `/onboarding`. 실패/취소: `/login?error=google`와 검증된 재시도 목적지만 반환한다.
- Google `(provider,sub)`로 내부 Account를 재사용하고 Principal.getName()은 내부 Account.id다. 이메일 기반 병합 없음. 세션/CSRF/STOMP 계약은 유지한다.
- 프로필·외부 설정·Google 실제 인증 미검증 상태는 [Google 로그인](GOOGLE_LOGIN.md)을 따른다.

브라우저의 `/homes`는 인증된 `GET /api/me`의 현재 가입 목록으로 구성한다. 서버가 허용한 집을 실제로 연 뒤 그 집 ID를 기기에서 계정별 화면 설정으로 기억한다. 집 화면의 권한은 매 진입 시 world API에서 확인한다.

## 가구·초대

- `POST /api/households` body `{name:"우리의 작은 집",memberName:"다은",appearance:"rose"}` → world. 대표 1명·반려동물 0마리로 시작하며 가족/반려동물 사전 설정을 받지 않는다.
- `GET /api/households/{id}/world` → 아래 스냅샷.
- `POST /api/households/{id}/invitations` → `{id,token,expiresAt,url:"/invite/{token}"}`. 대표만; 토큰은 생성 응답에서만 제공, DB에는 해시만 저장. 신규 발급은 7일 일회성 초대. 이미 발급된 초대는 저장된 만료 시각을 유지한다.
- `DELETE /api/households/{id}/invitations/{invitationId}` → 204. 대표만 취소.
- `GET /api/invitations/{token}` → `{householdName,expiresAt,available:true}`. 공개 미리보기이며 가입하지 않는다. 만료/취소/사용됨 410.
- `POST /api/invitations/{token}/accept` body `{memberName:"민준",appearance:"blue"}` → world. 인증 필수. 같은 계정 반복 수락은 동일 가구 반환, 다른 계정 사용 완료/만료 토큰 410. 가족 인원에 따른 제한은 없다.

## world 예시

```json
{"household":{"id":"1","name":"우리의 작은 집","ownerId":"1"},"meMemberId":"1","revision":1,"serverTime":"2026-11-01T00:00:00Z","members":[{"id":"1","userId":"1","name":"다은","appearance":"rose","role":"OWNER","place":"home","mood":"","food":"","thought":"","statusExpiresAt":null,"statusUpdatedAt":"2026-11-01T00:00:00Z","x":320,"y":320,"action":"idle"}],"pets":[],"pantryItems":[],"events":[]}
```

- place enum `home|office|school|outside`. 가입자는 접속이 없어도 유지한다. `members`는 수락 완료 가입자만. `pets` → `{id,name,kind,appearance}`.
- 공유 상태는 `PATCH /api/households/{id}/me/status` body `{place:"home",mood:"편안해",food:"파스타",thought:"오늘도 수고했어",expiresInMinutes:60}` → world. 빈 텍스트로 해제 가능, 1~1440분. 만료 필드는 스냅샷에서 빈 값으로 반환. 장소는 수동 공유 상태이며 관찰 전환과 별개.

## 식료품·사건

- `GET /api/households/{id}/pantry-items` → item array.
- `POST /api/households/{id}/pantry-items` body `{name:"우유",location:"냉장",quantity:2,unit:"개",expiresOn:"2026-11-06"}` → item `{id,name,location,quantity,unit,expiresOn,version,updatedAt}`.
- `PATCH /api/households/{id}/pantry-items/{itemId}` body `{quantity:1,version:0}` → item. 수량 ≥0, 버전 불일치 409. 저장시 사용 이력과 작성자 서버 저장.
- `GET /api/households/{id}/events` → event array (최신순 최대100).
- `POST /api/households/{id}/events` body `{requestId:"UUID",type:"ARRIVED_HOME"|"LEFT_WORK"|"PARCEL_ARRIVED"|"NOTE",message:"택배가 도착했어요"}` → `{id,type,message,authorMemberId,authorName,createdAt}`. 동일 작성자+requestId는 같은 사건을 반환, 상이한 재전송 본문은 409.

## STOMP

`/ws` 순수 WebSocket에 쿠키와 CONNECT `X-CSRF-TOKEN` 필수. `/topic/households/{id}/events` 구독 전 서버가 세션과 가구 가입을 검증한다. `/app/households/{id}/actions` 및 초기 복구용 `/app/households/{id}/sync` SEND만 허용. `/topic/**` 직접 발행, 타 가구 구독, 임의 목적지는 거절.

- 입력 `{requestId:"UUID",kind:"MOVE_INTENT",scene:"home",x:320,y:320}` 혹은 `{requestId:"UUID",kind:"INTERACT_INTENT",scene:"home",target:"sofa"}`.
- `scene`은 본인의 공유 장소와 같아야 한다. `memberId` 등 행위자 지정 금지. 논리 장면 640×448, 서버 경계/충돌/빈도 검증.
- 가구 이벤트 `{eventId,revision,type,serverTime,payload}`. 유형 `MEMBER_JOINED`, `STATUS_CHANGED`, `PANTRY_CHANGED`, `LIFE_EVENT_CREATED`, `AVATAR_ACTION_CHANGED`. 지속 변경은 DB 커밋 이후 발행.
- 개인 오류 `/user/queue/errors` → `{code,message}`.
- 클라이언트는 구독 후 스냅샷 조회 중 이벤트 버퍼링, eventId 중복 제거, revision 간격 발생시 스냅샷 재조회. 단순 브로커는 영구 로그가 아니다.

## 오류

`{code,message}`. 400 입력 오류, 401 로그인 필요, 403 가구/대표 권한 없음 또는 CSRF 실패, 404 데이터 없음, 409 버전/요청 충돌, 410 만료·취소·이미 사용된 초대.

구독 준비 절차: topic과 `/user/queue/errors`, `/user/queue/sync`를 구독하고 `/app/households/{id}/sync`로 `{}`를 보낸다. 서버의 `SYNC_READY`를 수신한 뒤 REST 스냅샷을 조회한다. 서버는 클라이언트 프레임 수신 순서를 유지하며, 조회 중 새 사건이 오면 최신 스냅샷을 다시 읽는다. 단순 브로커의 STOMP receipt 지원을 가정하지 않는다. 권한 위반 프레임은 정책 위반으로 소켓을 닫는다.

## 온보딩 외형 호환 (2026-11-02)

기존 POST /api/households의 appearance 문자열은 구형 sage/rose/blue/amber 외에 `v1:HTBA`를 사용한다. H/T/B/A는 각각 헤어/상의/하의/액세서리 인덱스 0~3이며 예시는 `v1:3212`다. 기존 24자 저장 필드·DTO·API 경로는 변경하지 않았다. world의 appearance를 그대로 보존하고 프런트 공통 변환기로 해석한다. [화면·저장·호환 검증](ONBOARDING.md).

## v0.9 생활 요청

- `GET /api/households/{h}/requests` → `{items,nextCursor}`. `state`는 쉼표로 분리한 상태, `limit` 기본 20·최대 100, `cursor`는 앞 페이지의 nextCursor다. 생성 시각과 ID 내림차순.
- `POST` 같은 경로에 `{requestId,kind,title,body?,targetMemberIds?,dueAt?}`. 제목 1~80자, 본문 0~300자. `NOTICE`는 ACTIVE, `TASK`는 OPEN, `PROMISE`는 본인이 담당하는 CLAIMED로 시작한다. 활성 가족만 대상이 될 수 있다.
- `GET /{id}`는 상세, `GET /{id}/history`는 상태 전이 이력. `POST /{id}/claim|release|complete|cancel|reopen`은 `{requestId,version,reason?}`. 취소·완료 취소에는 사유 1~200자가 필요하다. 다른 가족이 선점하면 409, 기한이 지난 요청의 전이는 410. 완료·취소는 기한이 지나도 기존 상태를 유지한다.
- 기한 지정은 현재부터 30일 이내. 생략하면 공지는 서울 시간 다음 자정(23시 이후 12시간 뒤), 부탁·약속은 7일 뒤다. 상태 변경은 가구 잠금으로 직렬화한다. `HOUSEHOLD_REQUEST_CHANGED`는 커밋 후 레코드 ID만 발행한다.
- 같은 작성/전이 명령 ID는 24시간 동안 같은 결과를 반환하며 다른 입력으로 재사용하면 409. 반응 및 알림 연결은 별도 작업이다.

- `PATCH /{id}`는 `{version,title?,body?,targetMemberIds?,dueAt?}`로 진행 중인 요청의 작성자 또는 집 대표가 수정한다. 생략 필드는 유지하며 본문 null은 비움, 대상 []는 모든 가족, 기한 null은 작성 시각 기준 기본값으로 되돌린다. 이미 맡은 가족을 새 대상 목록에서 빼거나 다른 담당자로 덮어쓸 수 없다.
- `POST /{id}/ack`는 게시 중인 공지에 요청자 자신의 확인만 기록한다. 동일 계정이 다시 호출하면 기존 `acknowledgedAt`을 받는다. 목록과 상세의 `acknowledgedByMe`는 요청자에게만 해당한다.

## v0.9 독립 표현

- GET `/api/households/{h}/me/state` → `{expressions,place,sharing,revision,serverTime}`. 본인의 `thought`, `food`, `mood`, `physical`, `availability`에 값·수정 시각·만료 시각·버전을 반환한다. 만료한 값은 null이다.
- PATCH 같은 경로, `{requestId,changes:{food:{value:"김밥",version:0,ttlMinutes:30}}}`. 생략한 항목은 보존하고 null은 해제한다. 값이 같고 TTL을 명시하지 않으면 버전과 기한을 유지한다. 표현은 30~1440분이며 기본 한마디 2시간, 음식 6시간, 나머지 4시간이다.
- 버전 충돌은 409이며 같은 배치 전체를 롤백한다. 24시간 안에 같은 명령 ID와 정규화된 입력으로 재시도하면 원래 결과를 반환한다. 다른 입력으로 같은 ID를 재사용하면 409다.
- 가족 world의 `members[].currentState`에는 현재 유효한 표현만 들어간다. 기존 평면 필드와 `/me/status`를 계속 읽을 수 있다. V10은 새 테이블 두 개를 추가하며 기존 멤버 행을 수정하지 않는다. 새 값이 저장되기 전까지는 각 기존 표현의 원래 만료 시각을 그대로 읽는다.
- 같은 changes에 `sharing:{enabled,version}`과 `place:{value,version,ttlMinutes?}`를 보낼 수 있다. 수동 장소 공개는 집별 동의가 필요하며 15~120분, 기본 120분이다. 공유를 끄면 마지막 수동 장소도 지운다. 구형 `/me/status`의 명시적 place 입력은 수동 공유로 변환한다.
- `members[].sharedPlace`는 `MANUAL`(현재 수동 장소), `UNKNOWN`(현재 미확인, 만료한 장소는 마지막 참고), `UNSHARED`(장소·시각 모두 숨김)를 구분한다. 다른 가족의 미확인/미공유 외부 가상 장면과 좌표는 중립 처리한다. 자신의 가상 장면과 집 내 아바타 움직임은 실제 위치와 별개다. 자동 위치 판정은 별도 작업이다.

## v0.6 사물 쪽지

활성 가족만 접근한다. h=가구 ID, object=현재 fridge만 허용한다.

- GET /api/households/{h}/objects/{object}/notes → ATTACHED 쪽지 배열.
- POST 같은 경로, `{body}` → 생성된 쪽지. 새 본문은 앞뒤 공백 제거 후 Unicode 코드 포인트 1~300자. 사물에 붙은 쪽지가 10장이면 409이며, 떼거나 삭제하면 자리가 생긴다.
- POST /api/households/{h}/notes/{id}/take, `{version}` → TAKEN 쪽지. 오래된 버전/이미 떼어낸 경우 409.
- GET /api/households/{h}/notes/archive → 기존 TAKEN 쪽지 배열.
- GET /api/households/{h}/notes/archive/page?cursor={cursor}&limit=20 → `{items,nextCursor}`. limit 기본 20·최대 100, 최초 요청은 cursor 생략. 생성 시각과 ID 내림차순이며, nextCursor가 null이면 마지막 페이지다.
- DELETE /api/households/{h}/notes/{id}?version=N → 204. 작성자 또는 대표만.

쪽지 DTO: `{id,objectId,authorMemberId,body,state,createdAt,takenByMemberId,takenAt,version}`. 본문은 일반 텍스트이며, 기존 300자 초과 쪽지는 원문 그대로 조회·보관할 수 있다. 작성자/가구/상태는 클라이언트가 지정하지 않는다.

## v0.6 공유 캘린더

- GET /api/households/{h}/calendar-events?from=YYYY-MM-DD&to=YYYY-MM-DD → 기간과 겹치는 일정 배열. 양 끝 날짜 포함, 최대 367일, 조회 날짜 경계는 Asia/Seoul.
- POST /api/households/{h}/calendar-events → 생성된 일정.
- PATCH /api/households/{h}/calendar-events/{id} → 수정된 일정. 버전 충돌 409.
- DELETE /api/households/{h}/calendar-events/{id}?version=N → 204.

입력 `{title,description,category,allDay,startDate,endDate,startAt,endAt,timeZone,version}`. title 1~120자, description 0~2000자. category=GENERAL|BIRTHDAY|ANNIVERSARY. 종일이면 LocalDate startDate/endDate만 입력(양 끝 포함), 시간이면 UTC ISO Instant startAt/endAt만 입력(시작 포함/종료 제외, 길이 0 허용). timeZone은 유효한 ZoneId, 웹 기본 Asia/Seoul. 응답은 입력 내용에 id, createdByMemberId, updatedByMemberId, createdAt, updatedAt, version을 포함한다. 작성자·수정자는 서버 인증에서 도출한다.

## v0.6 집들이 (가족 입주 Invitation과 별개)

대표만:

- POST /api/households/{h}/housewarming-invitations → `{id,url:"/visit/{token}",expiresAt}`. 7일, 해시 저장, 원문은 생성 때만 반환.
- GET 같은 경로 → `[{id,expiresAt,revokedAt}]`.
- DELETE /api/households/{h}/housewarming-invitations/{id} → 204. 기존 방문자 권한도 무효화.
- GET /api/households/{h}/visitors → `[{name,visitedAt}]`, 해당 링크의 최초 방문 시각.
- GET /api/households/{h}/guestbook → `[{id,visitorName,message,createdAt,hidden}]`.
- DELETE /api/households/{h}/guestbook/{id} → 204. 공개 목록에서 숨김.

Google 인증한 방문자:

- POST /api/visits/{token}/accept → 공개 방문 DTO 및 별도 방문 권한 생성/재사용. 명시 수락 전 GET은 403.
- GET /api/visits/{token} → `{householdName,theme,guestbook}`. 가족 world DTO와 다르다. 만료/취소 410, 미인증 401.
- POST /api/visits/{token}/guestbook, `{requestId,message}` → 방명록 DTO. message 1~1000자, requestId 1~80자. 같은 요청·같은 본문은 기존 결과, 같은 요청의 다른 본문은 409. 새 요청은 가구/계정별 30초 제한(429).

모든 변경 요청에 CSRF 필요. body에 작성자·가구·방문자 식별자를 주입하면 400. 방문 권한으로 가족 API 또는 가족 STOMP SEND/SUBSCRIBE에 접근할 수 없다. 가족 Membership를 생성하거나 변경하지 않는다.

쪽지/캘린더 변경은 기존 가족 topic으로 OBJECT_NOTE_CHANGED, CALENDAR_EVENT_CHANGED를 커밋 후 발행한다. payload는 변경된 레코드 ID이며 revision은 가구 전체 순서를 따른다. 클라이언트는 관련 REST를 재조회한다. [실제 검증/시간 정책](V06.md).

## 내 닉네임 변경

`PATCH /api/households/{id}/me/profile` — 세션 인증·CSRF·해당 가구의 활성 가입 필요. body: `{"memberName":"새 닉네임"}`. 앞뒤 공백을 제거하고 1~40자, 제어문자/줄바꿈을 거절한다. userId/memberId를 받지 않으며 서버가 현재 사용자의 구성원을 결정한다. 대표와 일반 구성원 모두 본인 이름만 변경한다. 성공은 갱신된 World, 가구에는 커밋 후 `MEMBER_PROFILE_CHANGED`를 발행해 재조회한다. 같은 이름 재저장은 revision을 올리지 않는다.

가구 캐릭터의 표시 이름 변경이며 Google 계정 이름·인증 식별자·다른 집의 캐릭터 이름은 변경하지 않는다. 기존 LifeEvent에 저장된 과거 작성자 이름은 당시 기록으로 유지한다. DB 마이그레이션은 없다.

## 반려동물 관리

- `POST /api/households/{id}/pets`: `{name:"모카",kind:"cat",appearance:"cream"}` → 갱신된 World. kind는 cat/dog, appearance는 현재 기본 cream. 이름 1~40자·공백 정리·제어문자 거절.
- `PATCH /api/households/{id}/pets/{petId}`: `{name:"보리",kind:"dog"}` → 갱신된 World. 기존 appearance는 그대로 유지한다.
- 목록은 기존 `GET /api/households/{id}/world`의 pets로 조회한다. 모든 활성 가족 조회 가능, 등록/수정은 대표만. 타 가구 petId·방문자·비대표는 403, 인증/CSRF/알 수 없는 필드 검증 유지.
- 성공 커밋 후 `PETS_CHANGED` 이벤트와 revision 갱신. 서버 응답/이벤트의 World로 사이드바와 Phaser를 갱신하며 새로고침을 요구하지 않는다.
- 삭제 API는 제공하지 않는다. 구형 POST households의 plannedPeople/pets는 제거된 계약이며 400이다. 계획 인원 PATCH는 제거했다.

## v0.7 공유 집 디자인 (v6.2 자산)

`POST /api/households`는 기존 name/memberName/appearance 외에 선택적 presetId를 받는다. 생략 시 garden_home. 허용 프리셋은 garden_home, sunny_apartment, brick_loft이며 classic_home은 기존 집 호환 별칭이다. 신규 appearance는 v2:adult_female, v2:adult_male, v2:child; 기존 v1:HTBA와 옛 값의 저장을 변경하지 않는다.

`GET /api/households/{id}/design` → `{presetId,version,variants}`. 활성 가족만 조회한다.

`PATCH /api/households/{id}/design` → 갱신된 World. 대표만 수정한다. 본문 예:

```json
{"presetId":"sunny_apartment","version":0,"variants":{"sofa":"blue","table":"round","fridge":"leaf"}}
```

variants의 키/값은 fridge=original|leaf|star, sofa=sage|blue|leather, table=wood|round다. 생략한 키는 선택 프리셋의 기본 외형이다. 클라이언트는 프리셋 변경 시 명시적 override를 유지해 전체 variants를 보낸다. version은 별도 디자인 버전이며 World.revision과 다르다. 401 미인증, 403 비대표/비가족/CSRF, 400 알 수 없는 값·위조 필드·음수 버전, 409 오래된 디자인 버전. 성공한 커밋 이후 SPACE_DESIGN_CHANGED 이벤트로 가족이 World를 재조회한다.

World에는 design을 추가했다. 기존 방문 전용 DTO도 householdName/theme/guestbook에 design만 추가한다. Membership, pantry, notes, calendar 등 비공개 world는 반환하지 않는다. 방문자는 가족 design API 또는 가족 STOMP에 접근할 수 없다.

`PATCH /api/households/{id}/me/profile`는 기존 memberName 외에 선택적 appearance(v2의 세 스타일만)를 받는다. 생략 시 기존 외형을 유지한다. 서버 principal로 본인의 Membership만 수정하며 MEMBER_PROFILE_CHANGED 커밋 후 이벤트를 유지한다. Google 내부 Account/sub는 변경하지 않는다.

home 좌표는 448×320 map의 발 중앙이며 서버와 웹이 같은 collision 카탈로그를 사용한다. 구형 좌표는 응답에서 안전한 spawn으로 투영하고 첫 새 이동 전까지 DB 원본을 보존한다. 다른 공간의 좌표 규칙은 유지한다. [상세](V62_INTEGRATION.md).
