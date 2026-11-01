# 모바일 온보딩 리뉴얼 — 2026-11-02

첨부 시안의 A/B/C를 하나의 순차 흐름으로 구현했다. 시안 PNG는 앱 자산에 포함하지 않았다. 기준 main은 4697f03이며 인증·가족 초대·REST 경로·WebSocket 정책과 DB 스키마는 유지했다.

## 구현 화면과 동작

1. Google 로그인: 기존 세션/가구 복구와 초대 목적지 우선 복귀를 유지한다.
2. A 새로운 시작: 픽셀 정원·집·캐릭터·반려동물 **일러스트**, 환영 말풍선, 새 집 생성과 초대 안내. 실제 소속 가구가 있을 때만 복귀 링크를 제공한다.
3. B 집 정보: 진행 표시, 집 이름의 실시간 미리보기, 사람/반려동물 스테퍼. 사람은 본인 포함 최소 1, 반려동물 최소 0. 제품상의 임의 인원 상한을 추가하지 않았다. 이 단계에는 서버 쓰기 요청이 없다.
4. C 캐릭터: 실제 조작 가능한 헤어·상의·하의·액세서리 각 4개, 즉시 미리보기, 캐릭터 이름, 반려동물별 이름·고양이/강아지 선택. 분류 탭은 방향키·Home·End도 지원한다.
5. 완료: 마지막 제출만 기존 POST /api/households를 호출한다. 성공하면 대표·반려동물이 저장되고 환영 화면 후 약 1.6초에 게임으로 이동한다. 바로 들어가기 버튼도 제공한다. 요청 중 버튼과 동기 잠금으로 중복 클릭을 막는다. 오류가 나면 입력과 외형은 유지된다.

초대 링크 입력은 현재 origin의 정확한 `/invite/{43자 토큰}` 경로만 허용한다. 다른 origin·쿼리·fragment·잘못된 토큰은 거부한다. 토큰은 draft에 저장하지 않으며 초대 화면 이동만 수행한다. 인증·명시적 수락 없이 입주시키지 않는다.

## 외형 호환과 저장

기존 `appearance` varchar(24)에 `v1:HTBA`(각 자리 0~3)를 저장한다. H=헤어, T=상의, B=하의, A=액세서리이며 예시 `v1:3212`는 7자다. 기존 sage/rose/blue/amber는 이전 옷 색과 기본 머리/하의로 해석한다. 알 수 없는 값은 기본 외형으로 안전하게 표시한다. 새 마이그레이션은 필요하지 않다. `appearance.ts`가 문자열을 게임 외형으로 변환하고 Portrait·온보딩·Phaser 모두 `makeAvatar`를 사용한다. 텍스처 키에는 전체 외형을 포함한다.

DB 왕복 통합 검사와 실제 HTTP 생성→world 재조회에서 새 외형 및 여러 반려동물을 확인한다. 같은 기본 테스트를 MySQL 8.4 CI도 상속한다. 캐릭터를 다시 만드는 로그인/복구 로직은 추가하지 않았다.

## 모바일·임시 입력

- viewport-fit=cover, top/bottom safe-area, 100dvh를 반영했다.
- 헤더는 작은 로고와 44×44 계정 버튼으로 구성한다. 계정 메뉴는 표시 이름·Google 계정·CSRF POST 로그아웃을 제공하고 Escape/바깥 클릭으로 닫힌다.
- 주요 CTA는 높이 52px이며 **문서 흐름 안에** 있다. fixed/sticky로 입력을 가리지 않는다. 작은 높이는 스크롤로 처리한다. 입력 글자는 16px이다.
- draft는 계정별 sessionStorage, 버전이 있는 키와 저장 시각으로 관리한다. 24시간이 지난 값·잘못된 수치는 복원하지 않는다. 탭 세션 범위를 넘는 영구 저장은 하지 않는다. 집 생성 성공/로그아웃 때 지운다. 저장소 사용이 차단돼도 입력·집 생성은 계속할 수 있다.
- OAuth 자격증명·세션·초대 토큰은 draft에 포함하지 않는다. 서버가 데이터의 원본이다.

## 아트와 로딩

기존 art.ts의 집 내부·가구·캐릭터·고양이·강아지 제작 코드를 재사용했다. 정원·구름·나무·울타리·기와집은 동일한 정수 픽셀 도형 방식으로 새로 그렸다. 외부 이미지나 신규 런타임 라이브러리는 추가하지 않았다. 미리보기는 2D canvas이며 Phaser를 시작하지 않는다.

Phaser와 WorldCanvas를 게임 진입 때 지연 로딩한다. 현재 production 빌드는 메인 JS 약 387kB(gzip 124kB), 게임 렌더러 약 12kB(gzip 5kB), Phaser 약 1,367kB(gzip 357kB)로 분리된다. 제작 시안의 미세한 일러스트 디테일까지 동일하게 복제한 것은 아니며, 실제 조작 가능한 원본 픽셀 아트로 해석했다.

## 자동 테스트·검증

- `bash scripts/pnpm.sh test`: 프런트 24/24. 신규 스토리, 기존 집 복귀, 안전한 초대 이동, 스테퍼 최소값, 단계 이동 전 쓰기 없음, 이전/새로고침 draft, 외형·다중 반려동물 최종 body, 0마리, 중복 제출, 오류 보존, 로그아웃/캐시/draft 정리, 키보드 분류 이동, 구형 외형 호환을 포함한다. 기존 Google·초대 복귀·실시간·이동 검사도 유지했다.
- `bash scripts/pnpm.sh build`: TypeScript/Vite 통과.
- `bash scripts/server.sh test bootJar`: 서버 32/32 및 JAR 빌드 통과. Google OIDC/CSRF/세션/가구 권한/초대 동시성/WebSocket 회귀와 외형·반려동물 DB 저장, HTTP 생성·재조회·미인증 거부를 포함한다.
- 코드 커밋 `d9ef1d2`의 [GitHub Actions 41359992314](https://github.com/hellomypp/dot-home/actions/runs/41359992314): web/server 모두 success. server의 `test mysqlTest bootJar`가 통과해 실제 MySQL 8.4에서도 기존 초대 동시성과 새 외형·반려동물·HTTP 생성 검사를 유지했다. 로컬 Mac에서 Docker/MySQL 테스트를 실행한 것으로 보고하지 않는다.

내장 브라우저에서 **실측 CSS 폭** 375·390·430·440·768·1280px의 A/B/C 18개 조합을 캡처했다. 모든 조합에서 scrollWidth=innerWidth이며 주요 CTA 높이 52px을 확인했다. [측정 원본](screenshots/onboarding/viewport-checks.json). 저장·이전/다음·네 가지 외형 변경·여러 반려동물 입력·새로고침 복원·완료 화면을 UI로 확인했고 개발 콘솔 오류는 없었다.

캡처는 개발 전용 `/studio/onboarding`의 **저장되지 않는 명시적 시안**이다. 인증 우회나 실제 API 저장을 제공하지 않으며 production 빌드에는 해당 경로가 없다. 실제 서버 저장은 위 HTTP/DB 통합 테스트로 검증했다. 내장 브라우저의 짧은 390×450 뷰포트 검사는 실제 Safari 소프트 키보드 검사가 아니다.

**이번 변경에 대한 실제 iPhone Safari 조작은 미검증이다.** 기존 Google 로그인 성공은 이전 사용자 사진으로 확인됐지만, 이번 전체 온보딩을 실제 Google 계정으로 생성→게임→재로그인하는 Safari E2E와 Safari 주소창/키보드 변화는 사용자의 실제 기기 확인이 남아 있다. 접근성의 VoiceOver 실기기 검증도 남아 있다.

## 실행과 iPhone 확인

```sh
cd ~/Developer/dot-home
# 기존 HTTPS 미리보기 유지/시작
python3 scripts/remote-preview.py start
python3 scripts/remote-preview.py status
# 필요가 없을 때 중지
python3 scripts/remote-preview.py stop
```

iPhone과 Mac의 Tailscale을 연결하고 Safari에서 `https://hs-macbook-pro.tailcd16d0.ts.net/`를 연다. 신규 Google 사용자는 A로, 기존 가구 사용자는 자신의 집으로 이동한다. 집이 있는 사용자가 새 시안을 실제 생성 없이 확인하려면 개발 서버의 `/studio/onboarding`을 사용한다. 실제 신규 온보딩은 `/onboarding`이다. 초대받은 가족은 받은 원래 초대 링크로 테스트한다. Serve는 tailnet 전용이며 Funnel을 사용하지 않는다.

실기기 체크: 상태바와 로고 간격, 하단 도구 모음 아래까지 스크롤 가능한 CTA, 각 입력의 키보드/닫기, 단계 앞뒤 이동, 여러 반려동물 입력, 집 생성 후 동일 외형의 게임 진입, 로그아웃과 재로그인 복원. 시안 제출은 저장되지 않으므로 실제 데이터 검증에는 Google 로그인한 `/onboarding`을 사용한다.

직접 로컬 실행은 기존과 동일하다.

```sh
APP_ORIGIN=http://127.0.0.1:5173 bash scripts/server.sh google
# 다른 터미널
bash scripts/dev.sh
```

## 수정 파일

- apps/web/src/onboarding/Onboarding.tsx, PixelScene.tsx, draft.ts, onboarding.css: 단계형 화면/아트 렌더링/draft/모바일 스타일.
- apps/web/src/lib/appearance.ts: 외형 버전·레거시 변환.
- apps/web/src/components/SessionBar.tsx, Portrait.tsx: 작은 계정 메뉴와 공통 도트 아바타.
- apps/web/src/App.tsx: 새 온보딩 통합·게임 외형 변환·게임 렌더러 지연 로딩·개발 전용 시안 경로.
- apps/web/src/game/art.ts, types.ts, index.ts: 원본 정원·집, 헤어/하의/장식 렌더링, 전체 외형 텍스처 키.
- apps/web/index.html, apps/web/src/styles.css: viewport-fit=cover와 공통 화면 safe area.
- apps/web/src/Onboarding.test.tsx, apps/server/src/test/java/kr/dothome/HouseholdIntegrationTest.java: UI 및 HTTP/DB 회귀 검사.
- README.md, docs/API.md, docs/OPERATIONS.md, docs/VERIFICATION.md, docs/ONBOARDING.md, docs/screenshots/onboarding/: 사용법·계약·검증·캡처.

## 화면 캡처

390px 모바일과 1280px 데스크톱의 기본 화면이다. 375/430/440/768px 캡처도 같은 폴더에 있다. 작은 화면에서는 아래로 스크롤해야 하단 CTA가 모두 보일 수 있다.

| 화면 | 모바일 390px | 데스크톱 1280px |
| --- | --- | --- |
| A 시작 | [이미지](screenshots/onboarding/390-start.jpg) | [이미지](screenshots/onboarding/1280-start.jpg) |
| B 집 정보 | [이미지](screenshots/onboarding/390-house.jpg) | [이미지](screenshots/onboarding/1280-house.jpg) |
| C 캐릭터 | [이미지](screenshots/onboarding/390-character.jpg) | [이미지](screenshots/onboarding/1280-character.jpg) |
| 입주 완료 | [이미지](screenshots/onboarding/390-complete.jpg) | [이미지](screenshots/onboarding/1280-complete.jpg) |

[다중 반려동물·하단 버튼](screenshots/onboarding/390-pets-and-actions.jpg) · [짧은 뷰포트](screenshots/onboarding/390-short-viewport.jpg) · [네 항목을 변경한 외형](screenshots/onboarding/1280-customized.jpg)

HTTPS 미리보기에서 /studio/onboarding 200, 최신 온보딩 소스 제공, googleEnabled=true/devEnabled=false를 확인했다. 저장되지 않는 시안에서 완료→Phaser 게임 진입도 확인했고 [게임 회귀 캡처](screenshots/onboarding/1280-game-regression.jpg)를 남겼다.
