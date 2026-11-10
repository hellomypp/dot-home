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

## v0.9 집 참여 종료

- `POST /api/households/{id}/me/leave`에 `{requestId}`를 보내 해당 집만 나간다. 같은 명령을 재시도해도 새 가입을 종료하지 않는다. 집 대표는 소유권을 이전해야 한다.
- `DELETE /api/households/{id}/members/{memberId}?version=…`는 대표가 다른 활성 가족을 퇴실시키는 요청이다. 버전은 world 멤버의 `membershipVersion`을 사용한다.
- 종료 후 해당 집 REST·STOMP 접근이 해제된다. 이전에 발급된 초대는 그 계정의 재가입에 사용할 수 없고, 이후 새 초대를 수락하면 새 Membership을 만든다.
- 공동 기록의 작성자 표시는 ‘퇴실한 구성원’으로 남고 개인 공유 일정과 현재 상태는 숨긴다. 진행 중 담당 요청의 담당자를 해제하고 활성 돌봄 프로필의 보호자를 대표로 이전한다.

### 집 대표 이전

- `GET /api/households/{id}/ownership-transfers`는 현재 제안에 참여한 가족에게만 제안을 반환한다. `POST`는 현재 대표가 `{requestId,targetMemberId}`로 한 명에게 제안하며 24시간 유효하다.
- 수신자만 `POST /{transferId}/accept`에 `{version,adultConfirmed:true}`를 보내 수락한다. 성인 여부는 수신자의 확인을 사용한다. `POST /{transferId}/cancel`은 제안자나 수신자가 `{version}`으로 취소한다.
- 수락 시 대표 한 명을 원자적으로 유지하고 이전 대표는 일반 가족으로 전환한다. 수신자가 먼저 퇴실하면 제안을 취소한다. 변경 알림에는 제안 대상이나 확인 내용이 포함되지 않는다.

### 공동 기록 내보내기

- `POST /api/households/{id}/exports`에 `{requestId}`를 보내면 202와 작업 ID를 받는다. `GET`은 본인의 최근 활성 작업, `GET /{exportId}`는 상태와 준비된 경우 5분 이내 만료되는 다운로드 토큰을 반환한다.
- `POST /{exportId}/download`에 `{token}`을 보낸다. 다운로드는 현재 참여 중인 같은 Membership과 CSRF 검증을 요구한다. 토큰은 URL에 넣지 않는다. 파일은 한 시간 안에 받을 수 있고 탈퇴·퇴실 시 서버의 파일을 지운다.
- JSON에는 공동 일정, 사물 쪽지, 부탁·공지, 생활 기록이 들어간다. 개인 공유 일정, 현재 상태, 인증·위치 원본, 계정 ID는 포함하지 않는다. 항목 종류별 1만 행 또는 전체 2MB를 초과하면 일부 기록을 조용히 누락하는 대신 작업이 제한 상태로 끝난다.

### 집 삭제 유예

- `POST /api/households/{id}/deletion`의 `{requestId,version}`은 대표의 7일 삭제 유예를 시작한다. `POST /deletion/cancel`의 `{version}`으로 기한 전 취소할 수 있다. world의 집 정보에 `pendingDeleteUntil`, `deletionVersion`, `deletionAvailable`이 추가된다.
- 대기 동안 활성 가족은 읽기와 내보내기를 이용한다. 쓰기·초대·방문을 막고 기존 방문 링크는 취소 후에도 복원하지 않는다. 예정 시각이 지나면 작업자가 이 집의 행을 정리하고 집 ID와 삭제 시각을 로그로 남긴다.
- 신규 삭제 요청과 예약 정리는 `HOUSEHOLD_DELETION_ENABLED`가 켜진 환경에서만 동작한다. 기본값은 false다.

## v0.9 달력 공유 범위

- 월간 조회의 `timeZone`은 선택 사항이며 기본 `Asia/Seoul`이다. 종일 일정의 LocalDate는 변환하지 않는다. 종료를 생략한 시간 일정은 시작 시각과 같은 순간으로 저장한다.
- 생성 시 `scope`는 `HOUSEHOLD`(기본) 또는 `PERSONAL_SHARED`다. 공동 일정은 작성자와 집 대표, 개인 공유 일정은 작성자만 수정·삭제한다. 등록 후 공유 종류는 고정한다. `CARE` 유형을 지원한다.
- 집 대표만 `POST /{id}/hide` 또는 `/unhide`에 `{version}`을 보내 개인 일정을 숨김·복원할 수 있다. `GET /moderation?from=…&to=…`는 숨긴 일정의 ID·숨김 시각·버전만 반환한다. 숨긴 일정은 작성자만 원문을 읽을 수 있고, 작성자가 비활성 상태이면 다른 가족에게 개인 일정이 노출되지 않는다.

- 선택 사항인 `targetIds`는 같은 집의 활성 가족과 등록된 아기·반려동물 ID 목록이다. 생략하면 이전 값 유지, 빈 배열은 모든 가족을 뜻한다. 아기와 반려동물은 돌봄 일정의 대상이며 참석 응답을 보내는 계정은 실제 가족뿐이다.
- `PUT /api/households/{h}/calendar-events/{id}/rsvp`의 본문은 `{status,version}`이다. 상태는 `ACCEPTED|DECLINED|TENTATIVE`, 최초 버전은 0이다. 서버가 응답자를 식별하며 대상이 아닌 가족은 403, 버전 충돌은 409를 받는다. 달력은 본인 응답 `myRsvp`와 가족 합계 `rsvpCounts`를 제공한다.
- `reminderMinutesBefore`는 선택 사항이며 허용값은 0·10·30·60·1440분이다. 명시적 null로 해제한다. 이 값의 저장과 실제 알림 발송은 별개다.

## v0.9 생활 요청

- `GET /api/households/{h}/requests` → `{items,nextCursor}`. `state`는 쉼표로 분리한 상태, `limit` 기본 20·최대 100, `cursor`는 앞 페이지의 nextCursor다. 생성 시각과 ID 내림차순.
- `POST` 같은 경로에 `{requestId,kind,title,body?,targetMemberIds?,dueAt?}`. 제목 1~80자, 본문 0~300자. `NOTICE`는 ACTIVE, `TASK`는 OPEN, `PROMISE`는 본인이 담당하는 CLAIMED로 시작한다. 활성 가족만 대상이 될 수 있다.
- `GET /{id}`는 상세, `GET /{id}/history`는 상태 전이 이력. `POST /{id}/claim|release|complete|cancel|reopen`은 `{requestId,version,reason?}`. 취소·완료 취소에는 사유 1~200자가 필요하다. 다른 가족이 선점하면 409, 기한이 지난 요청의 전이는 410. 완료·취소는 기한이 지나도 기존 상태를 유지한다.
- 기한 지정은 현재부터 30일 이내. 생략하면 공지는 서울 시간 다음 자정(23시 이후 12시간 뒤), 부탁·약속은 7일 뒤다. 상태 변경은 가구 잠금으로 직렬화한다. `HOUSEHOLD_REQUEST_CHANGED`는 커밋 후 레코드 ID만 발행한다.
- 같은 작성/전이 명령 ID는 24시간 동안 같은 결과를 반환하며 다른 입력으로 재사용하면 409. 알림 연결은 별도 작업이다.

- `PATCH /{id}`는 `{version,title?,body?,targetMemberIds?,dueAt?}`로 진행 중인 요청의 작성자 또는 집 대표가 수정한다. 생략 필드는 유지하며 본문 null은 비움, 대상 []는 모든 가족, 기한 null은 작성 시각 기준 기본값으로 되돌린다. 이미 맡은 가족을 새 대상 목록에서 빼거나 다른 담당자로 덮어쓸 수 없다.
- `POST /{id}/ack`는 게시 중인 공지에 요청자 자신의 확인만 기록한다. 동일 계정이 다시 호출하면 기존 `acknowledgedAt`을 받는다. 목록과 상세의 `acknowledgedByMe`는 요청자에게만 해당한다.

- `PROMISE` 생성에서 `sourceType`과 `sourceId`를 함께 보내면 원본 접근 권한을 검증하고 약속에 연결한다. 응답의 `sourceType`과 `GET /{id}/source`의 `{available}`은 원본 본문·식별자를 노출하지 않는다. 원본이 만료·삭제되어도 이미 확정한 약속과 명령 결과는 유지한다.

### 배려 반응

- `GET /api/households/{h}/reactions?sourceType=NOTE&sourceId={id}` → `{counts,mine}`. 가족에게 코드별 합계와 본인 반응만 반환한다.
- `POST` 같은 경로에 `{requestId,sourceType,sourceId,code}`. 원본은 `NOTE|REQUEST|EXPRESSION`. 표현 ID는 `memberId.field.version`이며 만료·교체된 표현은 410이다. 원본이 속한 집과 활성 가입을 확인한다.
- 코드 `THANKS|CHEER|ACK|SHARE_MEAL` 중 `SHARE_MEAL`은 현재 음식 표현에만 사용한다. 같은 구성원·원본·코드는 한 번만 집계한다. `DELETE /{id}`는 본인 반응만 지운다. 반응은 생활 요청·재고 변경을 만들지 않는다.

## 달빛 광산 가상 근무

- `PATCH /api/households/{id}/me/work-shift`에 `{working:true}`를 보내면 본인의 가상 장면이 달빛 광산으로 바뀐다. false는 근무를 마치고 집으로 돌아간다. 실제 장소나 공유 동의를 변경하지 않는다.
- 집별 위치 공유를 허용한 경우에만 다른 가족이 가상 근무 장면을 볼 수 있다. 근무 장면에서는 STOMP 이동·가구 조작을 거절한다. 광석·코인은 장면 연출이며 보유량에 기록하지 않는다.

## 학교 수업

- `PATCH /api/households/{id}/me/school-day`의 `{"attending":true}`는 본인을 교실로 이동시키고, `{"attending":false}`는 교실에 있는 본인을 집으로 돌려보낸다. 응답은 최신 world다. 같은 요청을 반복해도 상태가 같으면 revision을 올리지 않는다.
- `SCHOOL_DAY_CHANGED` 커밋 후 이벤트를 받으면 가족 클라이언트는 world를 다시 읽는다. 집별 위치 공유 동의가 있어야 다른 가족이 수업 장면을 볼 수 있다. 등교와 하교는 실제 위치나 공유 동의를 수정하지 않는다.
- 교실에서는 STOMP 이동·가구 조작이 거절된다. 주변 학생과 선생님은 수업 장면의 연출용 캐릭터다.

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

- `POST /api/households/{id}/pets`에 `{name,speciesGroup,speciesName?,guardianMemberId?}`를 보내면 갱신된 world를 반환한다. 활성 가족이 등록할 수 있고 보호자를 생략하면 본인이 맡는다. 이름은 1~40자, 직접 입력한 종명은 최대 80자다. 같은 집의 활성 가족만 보호자로 지정할 수 있다.
- 종 분류는 `DOG|CAT|BIRD|SMALL_MAMMAL|REPTILE|AMPHIBIAN|FISH|INVERTEBRATE|OTHER`다. 종에 맞춰 `habitat`과 `appearanceId`가 정해진다. 기존 `{name,kind,appearance}`의 cat/dog 및 cream 입력과 조회값을 유지한다. 그 밖의 종은 구형 kind에서 other로 표시된다.
- `PATCH /api/households/{id}/pets/{petId}`는 `{name,speciesGroup,speciesName?,guardianMemberId?,version}`을 받는다. 지정 보호자·집 대표만 수정하고 오래된 버전에는 409를 반환한다. 생략한 종명·보호자와 기존 appearance를 유지하며 빈 종명으로 지울 수 있다. 개·고양이의 구형 이름·종류 수정 요청을 계속 지원한다.
- 목록은 기존 `GET /api/households/{id}/world`의 pets로 조회한다. 성공 커밋 후 `PETS_CHANGED`와 revision으로 Phaser와 관리 목록을 갱신한다. 집들이 방문자·다른 집은 조회나 수정 권한이 없다.
- V17은 기존 이름·종류·외형을 보존하면서 종 분류와 기존 집 대표 보호자를 채운다. `DELETE /api/households/{id}/pets/{petId}?version=…`는 보호자·대표의 보관 요청이다. V19는 보관 시각을 남기고 집 장면 및 새 일정의 대상에서만 뺀다. 기존 일정은 보존한다. 추억 모드는 후속 작업이다.

- 어류 등록·수정의 `tankSlot`은 현재 집 프리셋의 배치판 칸 번호이며 world에 저장된다. 가구나 통로를 피한 지정 칸만 가능하고, 이미 쓰는 칸은 409다. 이전 어류 프로필은 기존 자동 위치를 계속 사용하며 수정 화면에서 자리를 지정할 수 있다. 프리셋을 바꿔 저장한 칸을 사용할 수 없으면 장면에서 자동 위치를 사용한다.

## 아기 돌봄 프로필

- `GET /api/households/{id}/dependents`와 world의 `dependents`는 활성 아기 프로필을 반환한다. 아기 프로필에는 계정이나 가족 Membership이 생기지 않는다.
- `POST /api/households/{id}/dependents`는 `{type:"BABY",name,guardianMemberId?}`를 받는다. 보호자를 생략하면 작성자가 맡는다. 기본 외형은 기존 `v2:child`이고 생년월일·건강정보는 수집하지 않는다.
- `PATCH /api/households/{id}/dependents/{profileId}`는 `{name,guardianMemberId?,version}`, `DELETE /api/households/{id}/dependents/{profileId}?version=…`는 보관이다. 보호자·대표만 실행하고, 보관된 아기는 새 일정에서 선택할 수 없다. 기존 일정과 대상 ID는 보존한다.
- 가구가 다르거나 집들이 방문자인 경우 접근을 거절한다. 변경 시 `DEPENDENTS_CHANGED`로 가족 화면을 갱신한다.

## 책장 추억 앨범

- 돌봄 프로필 삭제는 활성 목록에서 제외하고 기존 일정 이력을 보존한다. 같은 트랜잭션에서 기존 게임 스프라이트를 기반으로 만든 PNG와 삭제 시점의 이름을 가족 앨범에 저장한다. 이전에 보관된 프로필을 소급해서 추가하지 않는다.
- `GET /api/households/{id}/care-album?page=0`은 24장씩 `{items,hasMore}`를 반환한다. `DELETE /api/households/{id}/care-album/{photoId}`로 보호자 또는 집 대표가 사진을 삭제한다. 집 대표는 모든 사진을 지울 수 있다. 사진을 삭제하면 다시 만들어지지 않는다.
- 가족만 앨범을 볼 수 있다. 집 데이터 정리와 복원 시 삭제 로그 재적용에도 앨범이 포함된다.

## 반복 돌봄

- `GET /api/households/{id}/care-rules`는 활성 예약과 `nextAt`, `version`, `canEdit`를 반환한다. 활성 가족이 읽고 집 대표·대상 보호자가 설정한다.
- `POST`에 `{profileType:"PET"|"BABY",profileId,title,weekdays,localTime,timeZone}`을 보낸다. 요일은 영문 ISO 이름 목록이고 매일은 7개를 모두 선택한다. 시각은 HH:mm, 시간대는 IANA 이름이다. `PATCH /{ruleId}`는 같은 설정과 버전을 받으며 대상은 바꿀 수 없다.
- 예정 시각마다 생활 요청 한 건을 만든다. 시간대의 누락 시각은 다음 유효 시각, 중복 시각은 첫 시각을 사용한다. 재시작 후에는 24시간 이내의 가장 최근 발생 건만 복구한다. 요청의 `careScheduledAt`으로 자동 발생 건을 구분한다.
- `DELETE /{ruleId}?version=…` 또는 대상 보관은 반복을 종료하고 진행 중인 요청만 취소한다. 완료·만료 기록은 유지한다.

## v0.7 공유 집 디자인 (v6.2 자산)

`POST /api/households`는 기존 name/memberName/appearance 외에 선택적 presetId를 받는다. 생략 시 garden_home. 허용 프리셋은 garden_home, sunny_apartment, brick_loft이며 classic_home은 기존 집 호환 별칭이다. 신규 appearance는 v2:adult_female, v2:adult_male, v2:child; 기존 v1:HTBA와 옛 값의 저장을 변경하지 않는다.

`GET /api/households/{id}/design` → `{presetId,version,variants}`. 활성 가족만 조회한다.

`PATCH /api/households/{id}/design` → 갱신된 World. 대표만 수정한다. 본문 예:

```json
{"presetId":"sunny_apartment","version":0,"variants":{"sofa":"blue","table":"round","fridge":"leaf"}}
```

variants의 키/값은 fridge=original|leaf|star, sofa=sage|blue|leather, table=wood|round다. 생략한 키는 선택 프리셋의 기본 외형이다. 클라이언트는 프리셋 변경 시 명시적 override를 유지해 전체 variants를 보낸다. version은 별도 디자인 버전이며 World.revision과 다르다. 401 미인증, 403 비대표/비가족/CSRF, 400 알 수 없는 값·위조 필드·음수 버전, 409 오래된 디자인 버전. 성공한 커밋 이후 SPACE_DESIGN_CHANGED 이벤트로 가족이 World를 재조회한다.

World에는 design을 추가했다. 기존 방문 전용 DTO도 householdName/theme/guestbook에 design만 추가한다. Membership, pantry, notes, calendar 등 비공개 world는 반환하지 않는다. 방문자는 가족 design API 또는 가족 STOMP에 접근할 수 없다.

### 대표의 집 분위기 편집 세션

- `GET /api/households/{id}/edit-sessions` → `{session}`. 현 대표의 최신 세션을 반환하며 없으면 null이다. 세션에는 `id,status,draft,draftVersion,leaseUntil,expiresAt,committedVersion`이 들어간다. 초안은 기존 design DTO 형식이다.
- `POST /edit-sessions` 본문 `{requestId,recoverId?}`. 집당 활성 세션 1개이며 같은 시작 명령은 기존 세션을 반환한다. 다른 시작 명령이나 기존 `PATCH /design`은 활성 세션이 있으면 409 `EDIT_BUSY`다. 같은 디자인 버전을 기반으로 한 이전 초안만 새 세션으로 복구할 수 있다.
- 세션의 `PATCH /{sessionId}/draft`는 `{draftVersion,design}`을 검증해 비공개 초안만 갱신한다. `POST /{sessionId}/heartbeat`는 90초 임대를 연장한다. 세션의 전체 기한은 시작 후 20분이다. `POST /{sessionId}/cancel` 또는 임대 만료 후에는 같은 세션에서 저장할 수 없다.
- `POST /{sessionId}/commit` 본문 `{requestId,baseDesignVersion,draftVersion}`. 한 트랜잭션에서 디자인을 저장하고 `{committedVersion,world}`를 반환한다. 같은 커밋 명령이나 `GET /{sessionId}/result?requestId=...`로 저장 결과를 확인할 수 있다. 초안·결과의 조회 기간은 종료 후 24시간이다.
- 대표 이전, 가족 탈퇴·퇴실과 집 삭제 대기 시작 시 관련 세션과 승인을 종료한다. 초안은 편집자에게만 반환되며 공유 디자인은 커밋 이후 `SPACE_DESIGN_CHANGED` 이벤트로 갱신된다.

### 가족의 일회성 집 편집 승인

- `GET /api/households/{id}/edit-requests` → `{requests,activeEditor}`. 집 대표는 처리할 요청과 현재 편집자만 보고, 그 외 가족은 자신의 최신 요청만 본다.
- 가족의 `POST /edit-requests` 본문은 `{requestId,scope}`다. 현 지원 범위는 `PRESET`, `VARIANTS`, `LAYOUT`이며 여러 범위를 함께 요청할 수 있다. 활성 요청이 있으면 기존 요청을 반환한다. 대표의 `POST /edit-requests/{requestId}/approve|reject` 본문은 `{requestId,version}`이고, 요청자 또는 대표는 `POST /edit-requests/{requestId}/cancel`에 `{version}`을 보낸다.
- 대기 요청과 승인 후 세션 시작 기한은 각각 30분이다. 가족은 세션 시작 시 `approvalId`를 보낸다. 동시 편집으로 시작이 실패하면 승인은 남는다. 초안 및 커밋에서 승인 범위를 확인하고, 한 번 저장하거나 취소하면 권한을 소진한다.
- 대표는 `POST /edit-sessions/{sessionId}/revoke`로 활성 가족 세션을 회수한다. 가족은 새 승인을 받아 호환되는 초안을 이어갈 수 있다. 승인 변경은 내용 없는 `SPACE_EDIT_ACCESS_CHANGED` 이벤트로 알리고, 권한이 있는 계정만 요청함을 재조회한다.

### 가구 위치

- 공유 `design.positions`는 기본 위치에서 옮긴 사물만 `{objectId:{x,y}}`로 저장한다. 편집 세션의 초안에서 수정하며 카탈로그의 기본 위치 기준 16px씩 이동한다. 기존 `PATCH /design`은 같은 프리셋의 위치를 보존하고 새 프리셋에서는 기본 배치를 사용한다.
- 가구 충돌 영역·깊이·상호작용 지점은 위치와 함께 이동한다. 겹침, 수조 자리, 스폰과 주요 사물까지의 동선을 서버에서 검사한다. 구조물과 고정 사물 ID는 편집할 수 없다. 방문자는 공유 디자인으로 동일한 방을 렌더링한다.

`PATCH /api/households/{id}/me/profile`는 기존 memberName 외에 선택적 appearance(v2의 세 스타일만)를 받는다. 생략 시 기존 외형을 유지한다. 서버 principal로 본인의 Membership만 수정하며 MEMBER_PROFILE_CHANGED 커밋 후 이벤트를 유지한다. Google 내부 Account/sub는 변경하지 않는다.

home 좌표는 448×320 map의 발 중앙이며 서버와 웹이 같은 collision 카탈로그를 사용한다. 구형 좌표는 응답에서 안전한 spawn으로 투영하고 첫 새 이동 전까지 DB 원본을 보존한다. 다른 공간의 좌표 규칙은 유지한다. [상세](V62_INTEGRATION.md).
