# 도트홈 — 웹플랫폼 기술 선정 v0.5

> 2026-11-01 확정 변경: 공식 로그인은 Google OIDC 하나다. 아래 이전 공급자 선정 내용보다 최신 정책과 `GOOGLE_LOGIN.md`가 우선한다. 실제 인증 성공은 아직 미검증이다.
기준일: 2026-10-31 (Asia/Seoul). 사용자 결정은 웹플랫폼 우선과 Codex 구현이다. 아래 라이브러리·구성은 그 기준에 따른 선정안이다.

## 1. 기존 선정안의 변경

| 항목 | v0.4 | v0.5 |
|---|---|---|
| 플랫폼 | 모바일 우선 PWA와 앱 확장 | PC·태블릿·모바일 반응형 웹 브라우저 |
| 접속 | 설치·앱 확장 고려 | HTTPS URL 접속, 설치 불필요 |
| 실시간 | REST + SSE | REST + WebSocket(STOMP) |
| 프런트 | React·TypeScript·Vite·Phaser | 유지: 이미 웹에 적합 |
| 서버·DB | Java·Spring Boot·MySQL | 유지: 웹 계정·초대·공유 저장에도 필요 |
| 플랫폼 전용 | Capacitor·네이티브 위치 후보 | 현재 범위 제외, 후속 결정 |
| 공유 저장 원본 | 서버 DB | 유지: 로컬 저장만으로 가족 공유를 대체하지 않음 |
| 첫 출시 | 넓은 서비스 단계 | M0~M3를 완료한 공동생활 웹 MVP |

웹게임은 혼자 플레이하는 오프라인 게임이나 임의 가족 NPC로 바꾼다는 의미가 아니다. WebSocket은 웹에서 필수라서가 아니라 가족 간 이동 의도·행동·말풍선을 양방향으로 주고받는 경험에 맞춰 선정했다. 단방향 상태 통지에는 SSE도 적합하지만 이번에는 WebSocket으로 실시간 경로를 통일한다.

## 2. 스택과 역할

| 영역 | 기술 | 역할 |
|---|---|---|
| UI·언어 | React + TypeScript | 가입·초대·얼굴 목록·식료품·설정 |
| 개발·빌드 | Vite + pnpm | 개발 서버·정적 배포 결과 |
| 라우팅 | React Router | 초대·집·설정의 URL 접근 |
| 서버 데이터 | TanStack Query | REST 요청·캐시·갱신 |
| 도트 게임 | Phaser 4 계열 | 타일맵·이동·애니메이션·카메라·입력 |
| 서버 | Java 21 + Spring Boot 4.1 계열 | 도메인·API·실시간을 단일 앱으로 구현 |
| 인증·인가 | Spring Security + OAuth2 Client | 카카오 웹 로그인·서비스 세션·가구 권한 |
| 세션 | Spring Session JDBC | MySQL 기반 세션, 초기 Redis 없이 구성 |
| 저장 | MySQL 8.4 LTS + Spring Data JPA + Flyway | 관계·트랜잭션·스키마 변경 |
| 실시간 | Spring WebSocket/STOMP + @stomp/stompjs | 가구 이벤트·사용자 입력 의도 |
| 실행·CI | Gradle Wrapper + Docker Compose + GitHub Actions | 실행·검사·빌드 재현 |
| 검증 | Vitest·React Testing Library·Playwright / JUnit·Testcontainers | 로직·브라우저·인가·DB 검증 |

버전 계열은 선정 기준이며 통합 검증 완료를 뜻하지 않는다. 개발 착수 시 안정 버전과 공식 지원·플러그인 호환성을 확인하고 정확한 버전을 고정한다. Spring 의존성은 Boot의 관리 버전을 우선한다. 호환되지 않으면 대안과 변경 이유를 문서화한다.

## 3. 국내 기업 사례와 적합성

토스증권 공식 채용 자료는 React·TypeScript·Vite·pnpm·Playwright·GitHub Actions를 소개한다. 토스 프런트엔드 채용 자료에는 React Query와 WebSocket/SSE가 포함된다. 카카오페이 공식 기술 블로그는 Java/Kotlin·Spring·MySQL을 소개한다. 팀·시점별 사례이며 업계 전체 점유율이나 모든 서비스의 동일 구성을 뜻하지 않는다.

Phaser는 도트 게임 적합성으로 선택했으며 위 기업의 채택을 확인한 것은 아니다. Java는 개발자의 경험을 활용한다. Kafka·Kubernetes·마이크로서비스·Redis·외부 STOMP 브로커는 초기 필수가 아니다.

## 4. 브라우저·배포 기준

- PC Chrome·Edge·Firefox·Safari, iPhone/iPad Safari, Android Chrome을 검증 대상으로 한다. 실제 버전·기기는 QA 기록에 남긴다.
- PC의 마우스·키보드와 모바일 터치를 지원하고, 게임 캔버스와 일반 UI를 반응형으로 배치한다.
- 운영은 동일 HTTPS 출처를 권장한다. 정적 웹 파일과 /api·인증 경로·/ws를 웹 서버가 연결한다.
- 초기 운영안은 Linux 환경의 Nginx + Spring Boot 컨테이너와 MySQL이다. Docker Compose로 로컬 환경을 재현한다.
- 운영 DB 백업·복원, TLS, WebSocket Upgrade·timeout·heartbeat 설정을 검증한다.
- 예산·트래픽에 따라 CDN·관리형 MySQL·서버 확장을 검토한다. 특정 클라우드·요금은 아직 미정이다.
- STOMP simple broker는 단일 Spring 인스턴스를 전제로 한다. 서버 확장 시 세션 공유 외에 이벤트 전달·연결 라우팅도 별도 설계한다.

## 5. 현재 제외하는 플랫폼 기능

PWA 설치 유도·오프라인 서비스워커·Capacitor·네이티브 앱·앱스토어·브라우저 종료 후 지속 위치 추적은 현재 요구가 아니다. 게임 내 알림과 사건 이력은 포함한다. Web Push는 별도 후속 기능이며 WebSocket과 같지 않다. 배송·IoT는 제공자·기기 검증 후 웹 서버 연동으로 확장한다.

## 6. 공식 근거

- [토스증권 프런트 플랫폼](https://toss.im/career/job-detail?job_id=5130165003)
- [토스 프런트엔드](https://toss.im/career/job-detail?job_id=4076141003)
- [카카오페이 서버 환경](https://tech.kakaopay.com/post/happy-server-engineer/)
- [Phaser React·TypeScript 템플릿](https://github.com/phaserjs/template-react-ts)
- [Phaser 버전](https://phaser.io/download/archive)
- [Spring Boot 요구사항](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring STOMP](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/overview.html)
- [Spring WebSocket 보안](https://docs.spring.io/spring-security/reference/servlet/integrations/websocket.html)
- [MySQL LTS](https://dev.mysql.com/doc/refman/8.4/en/mysql-releases.html)
