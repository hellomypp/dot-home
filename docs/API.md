# 도트홈 구현 API 계약

개발 기준: 2026-11-01. 서버는 `/api`, STOMP endpoint `/ws`. 동일 출처 쿠키 세션. 모든 ID는 JSON 문자열, 시각은 ISO UTC. 공식 인증은 `google` 프로필의 Google OIDC. `dev`는 loopback 로컬 테스트에서만 허용하고 google/prod와 함께 시작할 수 없다.

## 인증

- `GET /api/auth/csrf` → `{token, headerName:"X-CSRF-TOKEN", parameterName:"_csrf"}`. 세션 쿠키를 유지하고 변경 요청마다 이 헤더를 전송한다. 로그인/로그아웃 뒤 재조회한다.
- `POST /api/auth/dev` body `{accountKey:"alice",name:"다은"}` → `GET /api/me`와 같은 응답. 계정 키는 로컬 검증 전용이며 같은 키는 같은 계정으로 복구한다.
- `GET /api/me` → `{user:{id,name},memberships:[{householdId,householdName,memberId,role:"OWNER"|"MEMBER"}]}`. 미인증 401.
- `POST /api/logout` → 204. CSRF 필수.
- Google 시작 `GET /oauth2/authorization/google`; 콜백 `/login/oauth2/code/google`. openid/profile/email만 요청한다.
- `GET /api/auth/config` → `{googleEnabled,devEnabled}`. 공개 설정 조회이며 인증 정보·토큰을 포함하지 않는다. UI는 서버 설정과 loopback 여부가 모두 맞을 때만 개발 폼을 표시한다.
- 초대 복귀: `/oauth2/authorization/google?next=/invite/{43자 URL-safe 토큰}`. 목적지는 OAuth state에 묶인 서버 세션에 저장하고 Google에 전달하지 않는다. 외부 URL·쿼리·fragment를 반환 경로로 허용하지 않는다.
- 성공: 원래 초대 화면 → 사용자의 명시적 수락. 목적지가 없으면 기존 가구 또는 `/onboarding`. 실패/취소: `/login?error=google`와 검증된 재시도 목적지만 반환한다.
- Google `(provider,sub)`로 내부 Account를 재사용하고 Principal.getName()은 내부 Account.id다. 이메일 기반 병합 없음. 세션/CSRF/STOMP 계약은 유지한다.
- 프로필·외부 설정·Google 실제 인증 미검증 상태는 [Google 로그인](GOOGLE_LOGIN.md)을 따른다.

## 가구·초대

- `POST /api/households` body `{name:"우리의 작은 집",plannedPeople:3,memberName:"다은",appearance:"rose",pets:[{name:"보리",kind:"cat",appearance:"cream"}]}` → world. 빈 pets 허용, 미가입 자리는 생성하지 않는다.
- `GET /api/households/{id}/world` → 아래 스냅샷.
- `PATCH /api/households/{id}` body `{plannedPeople:3}` → world. 대표만, 가입자 수 이하 불가.
- `POST /api/households/{id}/invitations` → `{id,token,expiresAt,url:"/invite/{token}"}`. 대표만; 토큰은 생성 응답에서만 제공, DB에는 해시만 저장. 72시간 일회성 초대.
- `DELETE /api/households/{id}/invitations/{invitationId}` → 204. 대표만 취소.
- `GET /api/invitations/{token}` → `{householdName,expiresAt,available:true}`. 공개 미리보기이며 가입하지 않는다. 만료/취소/사용됨 410.
- `POST /api/invitations/{token}/accept` body `{memberName:"민준",appearance:"blue"}` → world. 인증 필수. 같은 계정 반복 수락은 동일 가구 반환, 다른 계정 사용 완료/만료 토큰 410. 계획 인원 도달 409.

## world 예시

```json
{"household":{"id":"1","name":"우리의 작은 집","plannedPeople":3,"ownerId":"1"},"meMemberId":"1","revision":1,"serverTime":"2026-11-01T00:00:00Z","members":[{"id":"1","userId":"1","name":"다은","appearance":"rose","role":"OWNER","place":"home","mood":"","food":"","thought":"","statusExpiresAt":null,"statusUpdatedAt":"2026-11-01T00:00:00Z","x":320,"y":320,"action":"idle"}],"pets":[],"pantryItems":[],"events":[]}
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

`{code,message}`. 400 입력 오류, 401 로그인 필요, 403 가구/대표 권한 없음 또는 CSRF 실패, 404 데이터 없음, 409 인원/버전/요청 충돌, 410 만료·취소·이미 사용된 초대.

구독 준비 절차: topic과 `/user/queue/errors`, `/user/queue/sync`를 구독하고 `/app/households/{id}/sync`로 `{}`를 보낸다. 서버의 `SYNC_READY`를 수신한 뒤 REST 스냅샷을 조회한다. 서버는 클라이언트 프레임 수신 순서를 유지하며, 조회 중 새 사건이 오면 최신 스냅샷을 다시 읽는다. 단순 브로커의 STOMP receipt 지원을 가정하지 않는다. 권한 위반 프레임은 정책 위반으로 소켓을 닫는다.
