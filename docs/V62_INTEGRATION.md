# v6.2 그래픽 통합 · v0.7 공유 집 설정

2026-11-02. 개발 명세: https://app.notion.com/p/40a26d2089668150b006d5feb6334619 . 기준 main `6d331808c0f26f39b2f1a949c156ae56d2be3315`. 별도 `feat/v62-presets` 작업 트리에서 구현했으며 기존 5173/8080 미리보기 및 사용자 DB는 변경하지 않았다. 이번 작업은 로컬 검증이며 GitHub push·배포를 하지 않는다.

## 구현

- 온보딩: 집 이름과 선택 가능한 정원집·햇살 아파트·벽돌 로프트. 기본 정원집으로 바로 진행할 수 있다. 가족/반려동물 인원 사전 설정은 없다.
- 캐릭터: 제공된 adult_female / adult_male / child 시트를 스타일로 선택. 온보딩·초상화·게임은 같은 PNG를 사용한다. 실제 헤어/상의/하의 분리 자산이 없는 상태에서 가짜 꾸미기 탭을 표시하지 않는다. 설정에서 자신의 스타일만 변경할 수 있다.
- 집 설정: 대표만 프리셋 및 냉장고·소파·식탁 외형을 저장한다. 저장 전 미리보기와 취소는 로컬 상태다. 일반 가족은 공유 설정을 읽을 수 있다. 명시적으로 선택한 가구 외형은 프리셋 변경 후에도 유지한다.
- 저장: household 잠금 + 별도 design_version 비교로 충돌을 409로 거절한다. 일반 revision도 증가하며 트랜잭션 커밋 후 SPACE_DESIGN_CHANGED가 발행된다. 가족 클라이언트는 기존 STOMP 재조회/재연결 경로를 사용한다.
- 방문: 기존 가족 전용 world를 공개하지 않고 공개 DTO의 design만 추가했다. 방문자는 동일한 맵과 가구로 그린 읽기 전용 풍경을 본다. 방문 화면의 ‘집 모습 새로고침’으로 공개 DTO만 재조회해 최신 설정으로 복구한다. 방문자에게 가족 STOMP 권한을 부여하지 않는다.
- 게임: 실제 DB Membership·Pet만 렌더링한다. pack의 sampleActors를 가족으로 생성하지 않는다. 냉장고·달력·식탁·현관·반려동물 anchor를 기존 React 기능에 연결한다. dining_table은 기존 논리 ID table로 명시적으로 변환한다.

## 그래픽·물리 계약

`assets/space-catalog.json`이 클라이언트 및 JAR의 단일 맵/충돌 카탈로그다. 제공된 runtime JSON을 보존해 구성했다. 맵 448×320, 타일 16×16, 카메라 320×224, 정수 배율 2 또는 폭이 부족하면 1이다. 보간을 해제하고 그리기 좌표는 반올림한다.

사람은 24×40 frame, sheet 96×160, foot-center (0.5,1), 방향 down/left/right/up, idle 0, walk [1,2,3,2] / 150ms다. 사람 발 충돌 [-6,-8,12,8], 강아지 [-5,-5,10,5]를 사용한다. 가구 전체 그림을 충돌체로 삼지 않는다. 서버 MOVE 검증도 선택된 프리셋의 collision raster를 사용한다. 기존 직장·학교·밖은 별도 구형 공간 규격을 유지한다.

지면 -1000, 바닥 가구 -900, 건축 -800, 벽 장식 -700, 깊이 가구 metadata.depth, 사람 foot Y, 전경 10000으로 정렬한다. shared/occlusion.js의 후면 겹침 조건을 포팅해 해당 가구만 alpha .55까지 160ms에 전환하고 빠져나오면 복원한다. PNG 자체 alpha는 변경하지 않는다. 높은 책장 통로와 모든 상호작용 anchor의 도달 가능성을 테스트한다.

## 자산 출처·한계

사용자가 제공한 `dot-home-v6.2`와 캐릭터 ZIP을 사용했다. 원본 manifest 220개 레코드의 해시와 ZIP의 세 캐릭터 시트 일치를 확인했다. 출처 설명은 AI-assisted generation followed by deterministic native-pixel export다. 소유자의 미적 승인 및 최종 배포 권리 확인은 별도이며 승인 완료라고 표시하지 않는다.

`apps/web/public/art/v62/source-manifest.json`은 원본 기록, manifest.json은 실제 포함한 runtime subset 118개 원본 파일의 해시 목록이다. 추가 animation.json은 assets/animation.json의 동일 사본이다. 원본 PNG를 리사이즈·재색칠하지 않았다.

제공된 소파 3종과 식탁 2종을 재사용한다. 냉장고 원본은 하나이므로 초록·노란 자석은 코드로 그린 작은 픽셀 장식이다. 새 원본 냉장고 3종이라고 주장하지 않는다. 고양이 PNG는 제공되지 않아 기존 makeCat의 임시 Canvas 자산을 사용한다. 강아지는 제공된 보행 및 정적 sit/sleep 시트를 사용한다. 사람의 앉기/식사 전용 시트는 없다. 스토리 정원 배경은 기존 코드 일러스트이며 실제 실내 장면은 제공된 맵을 조합한다.

## 데이터 보존과 롤백

V1~V7을 수정하지 않고 V8만 추가했다. household에 preset_id(classic_home), design_version(0), object_variants({}), membership에 avatar_space(legacy)를 추가한다. 기존 ID·외형·좌표·초대·가족·반려동물 행을 삭제하지 않는다. classic_home은 garden_home의 호환 별칭이다.

구형 home 좌표는 크기 비율로 곱하지 않는다. avatar_space=legacy이면 응답에서 새 spawn으로 투영하고 원래 DB XY는 유지한다. 새 지도에서 첫 명시적 이동 이후 v62 좌표를 저장한다. 새 프리셋에서 유효하지 않은 좌표도 응답에서 안전한 spawn으로 투영한다. v1:HTBA/옛 색상 문자열은 DB에 남고 새 기본 스타일로만 표시한다(v1의 파란 상의→adult_male, 나머지→adult_female). 선택해서 저장할 때만 v2 스타일 값으로 교체한다. 이 매핑은 계정/성별/실제 나이를 바꾸지 않는다.

배포 전 별도 DB 백업과 복구 시험이 필요하다. V8 컬럼은 additive지만 새 좌표와 v2 외형을 구형 바이너리가 정확히 표시할 수 있다는 보장은 없다. 이전 버전으로 단순 JAR 교체를 안전한 데이터 롤백으로 간주하지 말고 호환 릴리스 또는 검증된 백업 복원 계획을 사용한다. 이번 작업에서는 라이브 DB 백업/마이그레이션/배포를 실행하지 않았다.

## 실행·검증

별도 QA 포트는 5174/8081, DB는 작업 트리의 ignored `.tools/v62-qa/db`다. Google 비밀값을 사용하지 않는 loopback 개발용 계정으로 HTTP/게임을 검사했다. 실행 중 JAR가 재빌드로 덮어써지지 않도록 build/libs에서 `.tools/v62-qa/app.jar`로 복사해 실행한다.

```bash
bash scripts/pnpm.sh install --frozen-lockfile
bash scripts/pnpm.sh test
bash scripts/pnpm.sh build
DOT_HOME_GRADLE_CACHE=/Users/yxxn/Developer/dot-home/apps/server/.gradle-local bash scripts/server.sh test bootJar
python3 scripts/test_v62_assets.py
python3 scripts/v62-smoke.py
python3 scripts/v06-smoke.py
```

공유 캐시 경로는 이 Mac의 선택적 최적화다. 다른 환경에서는 환경변수를 생략한다. smoke는 격리된 loopback 8081만 허용하며 실제 사용자 DB에 실행하지 않는다.

결과: web 50/50, server 72/72(실패·스킵 0), Vite build와 bootJar 통과. 실제 HTTP smoke에서 401/403/400/409/CSRF, 재로그인, override 유지, 공개 DTO·Membership 불변을 확인했다. Python 표준 unittest 5건(새 자산 검사 3건 포함) 통과. H2 V6→V7 및 V7→V8 데이터 보존, 동시 저장 단일 성공, STOMP 가족 변경 이벤트와 기존 Google/초대/쪽지/캘린더/집들이 회귀가 포함된다. QA 서버 재시작 후 기존 세션·집 설정 복구도 확인했다. 실제 Phaser 냉장고 사물 클릭 후 React 식료품/쪽지 모달 연결을 확인했다. 웹 빌드를 포함한 별도 JAR(8082)에서도 SPA 직접 경로, 원본 캐릭터 PNG 일치, immutable 캐시, 누락 404 및 API 401을 확인했다. 이 JAR 검증은 Docker/운영 MySQL 검증과 다르다.

`bash scripts/server.sh mysqlTest`는 Docker 엔진 부재로 컨테이너 5개 클래스 초기화에 실패했다. MySQL 8.4 신규 테스트는 추가했지만 이 Mac에서 실행 완료하지 못했다. Docker 이미지 빌드, 실제 Google 로그인 E2E, 실제 iPhone/Android/iPad Safari, 외부 HTTPS, GitHub Actions 신규 실행 및 배포는 미검증이다. 기존 CI 결과를 이번 커밋의 증거로 재사용하지 않는다.

내장 Chromium에서 CSS 뷰포트 실측 375/390/430/768 및 1280의 scrollWidth=innerWidth를 확인했다. 375~430은 320×224(1배), 1280은 640×448(2배) 게임 캔버스다. 스크린샷은 screenshots/v62에 저장한다. 실제 인증 STOMP 이동과 재접속 후 책장 후면에서 해당 가구가 흐려지는 장면도 캡처했다. 브라우저의 캡처 파일 치수와 CSS 뷰포트가 다를 수 있어 반응형 검증은 innerWidth/scrollWidth와 canvas DOM rect 실측으로 판정했다. 이는 실기기 Safari가 아니다.

## 변경 파일 안내

- 서버: Api.java, HomeService.java, Model.java, WorldSocket.java, Housewarming.java, Identity.java, WebDelivery.java에 호환 필드/공유 설정/좌표 검증/공개 asset GET을 반영했다. 신규 Spaces.java, SpaceController.java, V8__shared_space_design.sql, SpaceIntegrationTest 및 MySQL 파생 테스트를 추가했다. 기존 MigrationRetentionTest/SocketIntegrationTest는 새로운 DTO·spawn에 맞추고 회귀 검증을 유지했다.
- 웹: App.tsx, game/index.ts/types.ts, lib/api.ts, Onboarding.tsx/draft.ts/PixelScene.tsx, Portrait.tsx, Housewarming.tsx를 연결했다. 신규 game/nativeWorld.ts/spaces.ts, SpaceSettings.tsx/SpacePreview.tsx/CharacterStyleForm.tsx/spaces.css와 두 테스트 파일을 추가했다. 초대 수락도 새 스타일 선택을 사용한다.
- 자산/빌드: public/art/v62 원본 runtime subset, assets/space-catalog.json/manifest.json, Dockerfile 및 build.gradle. scripts/test_v62_assets.py, v62-smoke.py, v06-smoke.py, container-smoke.sh를 검증 계약에 맞췄다. container-smoke의 신규 실제 PNG/카탈로그/V8 검사는 추가했지만 Docker 부재로 실행하지 못했다.
- 문서: AGENTS/README, PRD/ART_DIRECTION/ASSETS/API/IMPLEMENTATION/ONBOARDING/ACCEPTANCE/VERIFICATION/KNOWN_ISSUES/DEPLOYMENT/CHANGELOG 및 이 문서. 과거 구현 이력과 v0.6 내용을 지우지 않고 현재 규격을 우선하는 설명을 추가했다.

GitHub push/배포는 하지 않는다. 커밋 SHA는 작업 완료 보고에서 확인한다. 로컬 QA 서버는 검증 종료 후 중지하고 ignored QA 파일 DB는 보존한다.
