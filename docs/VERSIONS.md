# 실제 고정 버전과 호환성 확인

확인일: 2026-11-01 (Asia/Seoul). 원본 v0.5 명세는 그대로 보존하며, 이 문서에 실제 구현 선택을 기록한다.

## 프런트엔드

npm 공식 레지스트리의 `latest` 메타데이터를 조회하고, 아래의 안정 버전을 범위 기호 없이 `apps/web/package.json`에 고정했다. 전이 의존성과 무결성은 `pnpm-lock.yaml`로 고정한다.

| 패키지 | 고정 버전 | 확인 근거 |
| --- | --- | --- |
| `react` | 19.3.1 | [npm registry](https://registry.npmjs.org/react/19.3.1) |
| `react-dom` | 19.3.1 | [npm registry](https://registry.npmjs.org/react-dom/19.3.1) |
| `typescript` | 7.0.2 | [npm registry](https://registry.npmjs.org/typescript/7.0.2) |
| `vite` | 8.3.5 | [npm registry](https://registry.npmjs.org/vite/8.3.5) |
| `@vitejs/plugin-react` | 6.1.2 | [npm registry](https://registry.npmjs.org/%40vitejs%2Fplugin-react/6.1.2) |
| `phaser` | 4.2.1 | [npm registry](https://registry.npmjs.org/phaser/4.2.1) |
| `@stomp/stompjs` | 7.5.0 | [npm registry](https://registry.npmjs.org/%40stomp%2Fstompjs/7.5.0) |
| `@tanstack/react-query` | 5.105.0 | [npm registry](https://registry.npmjs.org/%40tanstack%2Freact-query/5.105.0) |
| `react-router-dom` | 7.18.5 | [npm registry](https://registry.npmjs.org/react-router-dom/7.18.5) |
| `lucide-react` | 1.66.0 | [npm registry](https://registry.npmjs.org/lucide-react/1.66.0) |
| `vitest` | 5.0.5 | [npm registry](https://registry.npmjs.org/vitest/5.0.5) |
| `jsdom` | 30.1.3 | [npm registry](https://registry.npmjs.org/jsdom/30.1.3) |
| `@testing-library/react` | 16.3.3 | [npm registry](https://registry.npmjs.org/%40testing-library%2Freact/16.3.3) |
| `@testing-library/jest-dom` | 7.0.1 | [npm registry](https://registry.npmjs.org/%40testing-library%2Fjest-dom/7.0.1) |
| `@testing-library/user-event` | 14.6.8 | [npm registry](https://registry.npmjs.org/%40testing-library%2Fuser-event/14.6.8) |
| `@playwright/test` | 1.64.1 | [npm registry](https://registry.npmjs.org/%40playwright%2Ftest/1.64.1) |
| `@types/react` | 19.3.0 | [npm registry](https://registry.npmjs.org/%40types%2Freact/19.3.0) |
| `@types/react-dom` | 19.3.0 | [npm registry](https://registry.npmjs.org/%40types%2Freact-dom/19.3.0) |
| `@types/node` | 26.6.6 | [npm registry](https://registry.npmjs.org/%40types%2Fnode/26.6.6) |
| `@testing-library/dom` | 10.4.2 | [npm registry](https://registry.npmjs.org/%40testing-library%2Fdom/10.4.2) |

- Node.js 실행 검증 버전: **24.22.1**. `.nvmrc`에 고정하고 package.json은 24.15.0 이상 25 미만을 요구한다.
- Vite 8.3.5는 Node 20.19+ 또는 22.12+를 요구한다. 테스트용 jsdom 30.1.3은 Node 22.22.2+ / 24.15+ / 26+를 요구하므로 개발·CI를 Node 24 계열로 통일했다. 근거: [Vite 공식 시작 안내](https://vite.dev/guide/), 위 jsdom npm engines.
- React 19.3.1과 React DOM 19.3.1은 일치한다. TanStack Query, React Router, Lucide, Testing Library의 React 19 peer 범위를 확인했고 `strictPeerDependencies: true` 설치가 통과했다.
- Phaser 4.2.1은 [공식 아카이브](https://phaser.io/download/archive)의 4.x 안정 버전과 일치한다.
- Vite 8의 Rolldown 설정을 사용한다. Phaser를 별도 chunk로 분리해 일반 UI와 구분한다.
- pnpm은 이 환경에서 실제 실행한 **11.25.0**을 `packageManager`와 CI에 고정했다. 최신 조회값 12.16.1을 무조건 적용하지 않고 이미 검증한 설치기 버전을 유지한다. `bash scripts/pnpm.sh`는 시스템 Node/pnpm을 우선하고 없으면 Codex 번들 런타임을 사용한다.

## 서버

- 명세의 Java 21, Spring Boot 4.1 계열을 유지한다. 실제 빌드 파일은 Spring Boot **4.1.2**, Java toolchain **21**, dependency-management plugin **1.1.7**을 사용한다.
- Gradle Wrapper **9.2.1**에 배포 ZIP의 SHA-256을 함께 고정했다. Spring Boot 4.1.2의 공식 지원은 Gradle 8.14+ 및 9.x이고 Java 21은 지원 범위에 포함된다. [공식 시스템 요구사항](https://docs.spring.io/spring-boot/system-requirements.html)
- Spring Security, JPA, JDBC Session, Flyway, MySQL Connector 등은 Spring Boot BOM의 관리 버전을 따른다. 개별 최신 버전으로 임의 분리하지 않는다.
- MySQL은 명세의 **8.4 LTS** 계열을 대상으로 한다. H2 개발 프로필은 외부 DB 없이 독립 작업을 위한 용도이며 MySQL 통합 검증을 대체하지 않는다.

## 실제 실행 확인

- `bash scripts/pnpm.sh install --store-dir .pnpm-store`: 통과, 120 packages 설치, peer 의존성 오류 없음.
- `bash scripts/pnpm.sh install --frozen-lockfile --offline --store-dir .pnpm-store`: 통과, 잠금 파일 재현 확인.
- Vite 8.3.5 / TypeScript 7.0.2 / Vitest 5.0.5 / Playwright 1.64.1 CLI 실행 확인.
- `bash scripts/pnpm.sh --filter @dot-home/web exec tsc -p tsconfig.node.json`: 통과, Vite/Vitest 설정 타입 확인.
- 전체 제품 빌드, 단위 테스트, 브라우저, 서버·DB 검증 결과는 최종 실행 기록을 별도로 따른다. 이 문서의 버전 확인 자체가 제품 기능 검증을 뜻하지 않는다.

## 실행 경로

```sh
bash scripts/pnpm.sh install --frozen-lockfile
bash scripts/dev.sh        # http://localhost:5173
bash scripts/pnpm.sh build
bash scripts/pnpm.sh test
```

Vite는 `/api`, `/oauth2`, `/login/oauth2`, `/ws`를 로컬 Spring 8080으로 전달한다. 운영에서도 동일 출처 HTTPS 프록시가 필요하다. 앱에는 서비스워커/PWA 설치 경로를 추가하지 않았다.
