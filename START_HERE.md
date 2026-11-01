# 도트홈 — 웹게임 Codex 전달 패키지 v0.5

수정일: 2026-10-31 (Asia/Seoul). 이 버전이 이전 v0.4를 대체한다. 목표는 설치 없이 URL로 접속하는 반응형 공동생활 웹게임이다. 실제 개발은 Codex에서 수행한다.

## 전달 방법

1. 압축을 풀고 AGENTS.md와 docs가 있는 dot-home-codex 폴더를 Codex 프로젝트로 연다.
2. 기존 저장소라면 문서를 추가하고 기존 AGENTS.md와 병합한다. 기존 소스·지침을 덮어쓰지 않는다.
3. 원격 저장소 기반 Codex라면 이 문서들이 해당 저장소에도 있어야 한다.
4. 아래 지시로 시작한다. 자세한 원문은 CODEX_START_PROMPT.md에 있다.

```text
AGENTS.md와 CODEX_START_PROMPT.md를 읽고 도트홈 웹게임 개발을 시작해.
v0.5 웹플랫폼 명세를 기준으로 M0부터 순서대로 구현해.
가족 초대·참여·공유 저장을 유지하고 기존 데모 그래픽은 재사용하지 마.
```

## 포함 자료

| 파일 | 용도 |
|---|---|
| AGENTS.md | 반복해서 지켜야 할 제품·개발 규칙 |
| CODEX_START_PROMPT.md | 첫 작업 지시문 전체 |
| docs/PRD.md | 제품 요구·웹 MVP·미정 사항 |
| docs/TECH_STACK.md | 웹플랫폼 스택과 변경 이유 |
| docs/IMPLEMENTATION.md | 구조·데이터·API·실시간·개발 순서 |
| docs/ART_DIRECTION.md | 그래픽 제작과 브라우저 적용 기준 |
| docs/ACCEPTANCE.md | 단계별 검증·출시 기준 |
| docs/CHANGELOG.md | v0.4에서 변경한 내용 |
| references/README.md 및 HTML | 과거 동선 참고 자료와 한계 |

## 현재 결정

- PC·태블릿·모바일의 웹 브라우저에서 URL 접속한다.
- React·TypeScript·Vite·Phaser / Java·Spring Boot / MySQL / REST + WebSocket(STOMP).
- 가족 수·반려동물 수, 대표 생성·초대·인증, 미참여자 숨김, 얼굴 관찰, 자율 행동, 식료품 기록을 유지한다.
- M0 아트·장면 → M1 실제 가입·초대·저장 → M2 실시간 공동 공간 → M3 생활 정보·웹 내 알림이 웹 MVP다.
- Web Push·배송 API·IoT·고급 꾸미기는 후속 확장이다. PWA·네이티브 앱은 별도 결정 전까지 범위 밖이다.

## Notion 원본

- [제품 기획서](https://app.notion.com/p/40a26d20896681b8b9f1ed3031ebd172)
- [웹게임 개발 전달서](https://app.notion.com/p/40a26d2089668150b006d5feb6334619)

이 패키지는 Notion과 맞춘 개발용 스냅샷이다. 이후 결정은 버전·변경 이력을 함께 수정한다. Notion 연결 없이도 이 파일로 시작할 수 있다. 이 채팅 내용이 다른 Codex 환경에 자동 전달된다고 가정하지 않는다.

현재 완료된 것은 개발 명세다. 이번 수정에서 실제 웹게임·서버·DB·새 그래픽을 제작하지 않았다. 과거 HTML은 참고 자료로 보존한다.
