# iPhone tailnet 원격 미리보기

## 현재 상태

Tailscale Serve와 두 LaunchAgent가 활성화되어 있다. iPhone은 같은 tailnet에 연결한 뒤 [도트홈 미리보기](https://hs-macbook-pro.tailcd16d0.ts.net/)를 열면 된다. 최신 주소와 Serve 설정은 `status`로 확인한다.

## Mac에서 필요한 승인

Mac의 대화형 터미널에서 `brew install --cask tailscale` 실행 후 관리자 승인을 완료한다. 앱을 열고 macOS가 요청하는 VPN/네트워크 확장 승인을 완료하고 본인의 Tailscale 계정으로 로그인한다. iPhone에도 Tailscale을 설치하여 같은 tailnet에 연결한다. 암호·인증 토큰은 채팅이나 저장소에 기록하지 않는다. Tailnet의 MagicDNS·HTTPS 인증서 사용 승인이 필요할 수 있다.

## 실행

프로젝트 루트에서 기존 5173/8080 개발 서버를 해당 터미널의 Ctrl+C로 정상 종료한다. 기존 Serve 설정이 있으면 먼저 확인한다. 스크립트는 포트 충돌이나 기존 Serve 구성을 발견하면 덮어쓰지 않고 중단한다.

```sh
python3 scripts/remote-preview.py start
python3 scripts/remote-preview.py status
python3 scripts/remote-preview.py upgrade
python3 scripts/remote-preview.py stop
```

start는 현재 Mac의 실제 DNSName을 `tailscale status --json`으로 읽는다. https://실제이름.ts.net 을 APP_ORIGIN으로 설정하고 동일 호스트 하나만 Vite allowedHosts에 넣는다. 가짜 도메인·와일드카드는 사용하지 않는다. Serve는 `tailscale serve --bg --https=443 http://127.0.0.1:5173`만 사용하며 Funnel을 사용하지 않는다. 기존 Serve 설정을 자동 초기화하지 않는다. HTTPS 기능 활성화 링크가 출력되면 사용자가 승인한 뒤 실행 상태를 다시 확인한다.

로그인 세션의 LaunchAgent 두 개(`kr.dothome.preview.server`, `kr.dothome.preview.web`)가 개발 서버를 유지하고 종료 시 재시작한다. Codex 종료와 독립적이다. Mac 로그아웃·수면·전원 종료 동안은 접속할 수 없다. LaunchAgent는 ~/Library/LaunchAgents에 생성되며 stop은 두 job을 해제하고 생성 파일을 제거한다. Serve 443도 끈다. 로그는 무시되는 `.tools/remote-preview/`에 남는다. plist에는 로컬 경로·도메인만 들어가며 토큰을 저장하지 않는다.

`upgrade`는 기본 로컬 H2로 실행 중인 백엔드를 대상으로 한다. 새 JAR를 먼저 빌드하고 백엔드를 정상 종료한 뒤 기존 파일 DB와 LaunchAgent 설정을 `.tools/preview-upgrade-backups/`에 저장한다. 새 서버가 준비되지 않으면 이전 LaunchAgent 설정으로 다시 기동을 시도한다. 백업으로 현재 DB를 덮어쓰는 복구는 별도로 판단한다. Vite와 Serve는 업데이트 중에도 실행된다.

## 연결 구조 및 보안

Safari HTTPS → Tailscale Serve → loopback Vite5173 → /api 및 /ws 프록시 → loopback Spring8080. 양쪽 개발 서버는 127.0.0.1에만 바인딩한다. Spring CSRF·쿠키 세션·STOMP CONNECT 토큰과 정확한 Origin 검증을 유지한다. 원격 실행은 Secure 쿠키를 사용한다. 브라우저의 기존 상대 REST URL과 wss://현재호스트/ws를 그대로 사용한다.

개발 인증은 비밀번호 없는 테스트 방식이다. tailnet에서 해당 Mac에 접근 권한을 가진 사람은 개발 로그인에 접근할 수 있으므로 Tailscale 기기/사용자 ACL을 본인 기기에 맞게 제한한다. 스크립트가 tailnet ACL을 임의 변경하지 않는다.

## 설치 후 검증

1. status가 출력하는 실제 HTTPS URL에서 `/studio`를 열어 터치 이동·말풍선·화면 회전을 확인한다.
2. `/api/auth/csrf`가 JSON 응답하는지 확인한다. 토큰 원문을 공유하지 않는다.
3. `/`에서 별도 테스트 계정으로 로그인하고 집을 만든다. 반려동물 0마리와 미참여자 미표시를 확인한다.
4. 게임 헤더 ‘연결됨’을 확인한다. 이는 STOMP 연결과 SYNC_READY 응답 이후 표시된다.
5. Mac과 iPhone에서 서로 다른 테스트 계정으로 초대를 수락하고 기분·재고·사건 공유를 확인한다.
6. Safari를 백그라운드로 보냈다가 복귀해 재동기화를 확인한다.
7. stop 후 HTTPS 접속과 두 로컬 서비스가 중단되는지 확인한다.

Serve/기기 설치 공식 근거: https://tailscale.com/docs/reference/tailscale-cli/serve , https://tailscale.com/docs/install/mac

## 연결 결과 (2026-11-01)

실제 주소: https://hs-macbook-pro.tailcd16d0.ts.net
Serve 활성화 완료, Funnel 미사용. HTTPS /studio 200 및 HTTPS 경유 독립 계정의 로그인·집 생성·초대·재고 충돌·사건 저장 smoke 통과. 실제 iPhone Safari와 HTTPS STOMP 검증은 아직 미수행.

LaunchAgent는 macOS Documents 접근 제한(Operation not permitted)으로 실행에 실패하여 bootout했다. 현재는 직접 실행한 개발 서버로 HTTPS 접속을 제공한다. 따라서 Codex 종료와 독립적인 유지 요구는 아직 충족하지 못했다. macOS 백그라운드 Documents 접근 승인 또는 사용자가 승인한 실행 위치 변경이 필요하다. 이 승인을 우회하지 않았다. 기존 start는 승인 해결 전 반복 실행하지 않는다.

## 실행 위치 이전 후 결과

사용자 승인으로 프로젝트 전체(기존 DB·Git 포함)를 `/Users/yxxn/Developer/dot-home`으로 이동했다. 위 Documents 차단은 새 위치에서 해결되었다. LaunchAgent 두 개가 실제 실행되고 있으며 Serve는 동일한 tailnet HTTPS 주소를 유지한다. HTTPS `/studio` 200 및 HTTPS REST smoke를 다시 통과했다. 실제 iPhone Safari·원격 STOMP 검증은 아직 남아 있다.

이후 실행·중지 명령은 반드시 새 위치에서 사용한다:

```sh
cd ~/Developer/dot-home
python3 scripts/remote-preview.py status
python3 scripts/remote-preview.py stop
python3 scripts/remote-preview.py start
```

웹 실행 스크립트는 pnpm의 중첩 호출을 제거하여 launchd의 제한된 PATH에서도 동작하도록 수정했다. 코드 기능은 변경하지 않았다.


## Google 공식 로그인 전환

현재 원격 실행은 dev 대신 local(인증 정보가 있으면 local,google)로 시작한다. 과거 개발 계정 로그인 설명은 로컬 테스트 기록이다. 원격 임의 계정 키 로그인은 사용할 수 없다. `.tools/google.env`에 Google 서버 인증 정보를 안전하게 저장한 뒤 stop/start 한다. Google 정보가 없을 때는 /studio와 설정 대기 화면을 제공한다. 초대와 실제 계정 E2E 절차는 [Google 로그인](GOOGLE_LOGIN.md)을 따른다.


원격 서버는 start 때 bootJar를 빌드한 뒤 JVM을 직접 launchd job으로 실행한다. Gradle bootRun을 백그라운드 job으로 계속 두지 않는다. stop은 정상 종료를 최대 15초 기다려 바로 재시작할 때 포트 충돌을 줄인다.
