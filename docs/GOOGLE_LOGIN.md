# Google 로그인 구현과 외부 설정

## 확정 정책과 구현 범위

공식 인증은 Google OAuth 2.0 Authorization Code + OpenID Connect 하나다. 별도 ID/비밀번호 가입이나 이메일 기반 계정 병합은 없다. Google 인증은 사용자 확인, 초대는 가구 입주 권한이다. 로그인을 마쳐도 초대를 수락하기 전에는 입주하지 않는다.

GoogleIdentity는 표준 OidcUserService.loadUser를 호출한다. ID Token의 서명·issuer·audience·nonce 및 OAuth state 처리는 Spring Security 기본 구현에 맡긴다. 검증된 sub로 IdentityService.resolve("google", sub, 초기표시이름)를 호출하고, OidcUser 래퍼의 getName()만 내부 Account.id로 매핑한다. getSubject()/claims는 원본 OIDC 의미를 유지한다. 이메일은 조회 키가 아니다. 이름은 제어문자를 제거하고 비어 있으면 ‘새 구성원’, 길이는 40자로 제한한다. 기존 내부 계정의 이름·캐릭터를 재로그인으로 덮어쓰지 않는다.

기존 Account(provider,subject) 고유 제약과 Membership를 그대로 사용한다. 이전 개발용 계정은 보존되지만 Google 계정으로 자동 연결하지 않는다. 새 Google 계정에 기존 개발 캐릭터가 자동 이관되는 기능은 없다. 다중 공급자·사용자 주도 계정 연결이 필요해지면 별도 정책과 검증을 설계한다.

## 초대 목적지 보존

로그인 링크는 /oauth2/authorization/google?next=/invite/토큰 형식이다. next는 정확한 43자 URL-safe 초대 토큰 경로만 허용한다. 외부 URL·프로토콜 상대 URL·쿼리·fragment·개행은 거부한다.

목적지는 OAuth2AuthorizationRequest의 attributes에 담아 HttpSessionOAuth2AuthorizationRequestRepository로 서버 세션에 보관한다. Google에 보내는 추가 파라미터가 아니다. 콜백에서 올바른 state로 저장된 요청을 회수한 경우에만 목적지를 요청 속성으로 옮긴다. 성공 시 해당 초대 화면으로 돌아가고 사용자가 이름·외형을 설정해 명시적으로 수락한다. 목적지가 없으면 기존 가구 또는 /onboarding으로 보낸다. 실패·취소는 정해진 /login?error=google 안내로 돌아가며 공급자의 오류 내용·토큰을 URL에 노출하지 않는다. 유효한 원래 초대 목적지는 재시도용으로만 보존한다.

동시에 여러 로그인 탭을 열면 표준 세션 저장소의 마지막 authorization request가 이전 흐름을 대체할 수 있다. 이 경우 state가 맞지 않는 흐름은 안전하게 실패하며, 잘못된 초대로 자동 입주하지 않는다.

## Google Cloud에서 사용자가 할 일

1. Google Cloud 프로젝트를 생성하거나 기존 프로젝트를 선택한다.
2. Google Auth Platform의 Branding에 앱 이름·지원 이메일 등을 입력한다. Audience에서 Internal/External 대상 사용자를 올바르게 선택한다.
3. Data Access는 openid, profile, email만 사용한다. 다른 API 권한은 요청하지 않는다.
4. Clients에서 OAuth Client 유형 Web application을 생성한다.
5. Authorized redirect URIs에 사용할 환경별 정확한 주소를 등록한다. scheme·host·port·path까지 일치해야 한다.
6. External Testing 상태라면 테스트에 사용할 Google 계정을 Test users에 등록한다.
7. 발급된 Client ID와 Client Secret을 Mac의 .tools/google.env 또는 서버 비밀값 환경변수에 저장한다. 채팅·GitHub·프런트 VITE_ 환경변수로 전달하지 않는다.

콜백 후보:

- 로컬: http://127.0.0.1:5173/login/oauth2/code/google
- 대체 로컬: http://localhost:5173/login/oauth2/code/google (이 경우 APP_ORIGIN도 localhost로 일치)
- 현재 tailnet: https://hs-macbook-pro.tailcd16d0.ts.net/login/oauth2/code/google
- 운영: https://실제도메인/login/oauth2/code/google

Google은 localhost/loopback의 HTTP 예외를 허용하지만 실제 Cloud Console 저장과 인증 성공은 별도 확인해야 한다. 임의 공개 HTTP 주소는 쓰지 않는다. ts.net 테스트 주소는 Google Console의 도메인/브랜드 정책에서 실제 수락되는지도 확인해야 한다. 인증서나 승인 도메인 오류를 우회하지 않는다. 등록할 수 없으면 소유·검증 가능한 HTTPS 테스트 도메인을 사용한다.

## Mac 로컬 실행

```sh
cd ~/Developer/dot-home
mkdir -p .tools
# .tools/google.env를 텍스트 편집기로 만들고 실제 값 입력:
# GOOGLE_CLIENT_ID=...
# GOOGLE_CLIENT_SECRET=...
chmod 600 .tools/google.env
APP_ORIGIN=http://127.0.0.1:5173 bash scripts/server.sh google
# 다른 터미널
bash scripts/dev.sh
```

server.sh google은 local,google 프로필과 파일 DB를 사용하고 .tools/google.env가 있으면 읽는다. HTTP loopback에서만 Secure 쿠키 기본값을 false로 지정한다. 외부 HTTPS에서는 true다. local은 DB 실행 환경일 뿐 개발 인증을 켜지 않는다. 실제 운영은 MySQL DB 환경변수와 google,prod 프로필로 JAR를 실행한다. PORT도 필수다. [Railway 상세 지침](DEPLOYMENT.md)을 따른다.

```sh
APP_ORIGIN=https://실제도메인 \
java -jar apps/server/build/libs/dot-home-server-0.1.0.jar --spring.profiles.active=google,prod
```

GOOGLE_CLIENT_ID/SECRET, DB_URL/USER/PASSWORD는 실행 환경에서 별도로 주입한다. 기존 12h 세션·HttpOnly·SameSite=Lax·CSRF·세션 ID 교체·로그아웃 소켓 정리를 유지하며 remember-me는 추가하지 않는다.

## iPhone·가족 HTTPS 테스트

1. Mac과 iPhone을 같은 tailnet에 연결한다. Google에서 등록한 HTTPS 콜백과 APP_ORIGIN을 일치시킨다.
2. .tools/google.env에 실제 인증 정보를 저장하고 remote-preview.py stop 후 start로 서버를 다시 실행한다.
3. /api/auth/config에서 googleEnabled=true, devEnabled=false인지 확인한다. 비밀값은 응답하지 않는다.
4. Safari 또는 Chrome에서 Google 로그인 → 첫 사용자 집 생성 → 재로그인 후 동일 캐릭터 복구를 확인한다.
5. 다른 Google 테스트 계정에 초대를 보내고 인증 후 원래 초대 화면으로 복귀하는지 확인한다. 수락 전에는 대표 화면에 캐릭터가 나타나면 안 된다.
6. ‘입주하기’ 후 양쪽 게임 헤더 연결됨, 기분·식료품·생활 사건 공유, 로그아웃 후 소켓 종료를 확인한다.
7. 취소·만료·사용된 초대·동일 토큰 동시 수락·타 가구 접근을 확인한다.

preview는 인증 정보가 있으면 local,google을 사용하고, 없으면 local만 사용해 /studio와 설정 대기 화면을 제공한다. 원격 preview에서 dev 프로필을 사용하지 않는다. 개발 계정 로그인은 별도 loopback dev 서버에서만 허용한다.

## 프록시 신뢰 경계

Vite는 /api, /oauth2, /login/oauth2, /ws를 loopback Spring에 전달하고 Host를 외부 호스트 그대로 유지한다. allowedHosts는 실제 미리보기 호스트 하나만 허용한다. Spring은 기본적으로 loopback에 바인딩하고 forward-headers-strategy=none으로 전달 헤더를 신뢰하지 않는다. OAuth callback은 APP_ORIGIN에서 명시적으로 구성하므로 Host/X-Forwarded-Host/X-Forwarded-Proto를 위조해도 바뀌지 않는다. 새 프록시를 사용할 때도 신뢰 프록시 범위를 확정하기 전 전체 forwarded-header 처리를 켜지 않는다.

초대 원문이 들어가는 /invite, /api/invitations, /oauth2/authorization/google?next= 및 code/state가 있는 callback 경로는 접근 로그·분석·오류 수집에서 마스킹/제외한다. Referrer-Policy는 no-referrer다. Google Access/ID Token은 서버 인증 처리에만 사용하고 브라우저 localStorage에 저장하지 않는다.

## 실제 검증 상태

Mock 기반으로 내부 사용자 매핑·재로그인·이메일 비병합·세션 직렬화·초대 state/목적지·오류/취소·고정 callback·가구 생성/복구·운영 dev 차단을 검사한다. 사용자가 제공한 실제 Google Cloud 인증 정보를 Git에서 제외된 서버 전용 파일에 적용했다. 실제 Google 계정 선택 화면까지 연결되고, HTTPS callback·scope·state·nonce가 확인됐다. 계정 선택과 동의 완료 이후의 토큰 교환, Google → tailnet 콜백, Safari Google E2E는 아직 미검증이다. 모킹 결과를 실제 OAuth 성공으로 보고하지 않는다.

기존 MySQL 초대 동시성 실패는 docs/KNOWN_ISSUES.md에서 별도 관리한다.

공식 자료: [Spring Security OAuth2 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/advanced.html), [Google OIDC](https://developers.google.com/identity/openid-connect/openid-connect), [Web Server OAuth](https://developers.google.com/identity/protocols/oauth2/web-server).
