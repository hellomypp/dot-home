# 미해결 이슈

## 2026-11-11 V39 인수 범위

- 비공개 Tailscale과 실제 Chrome/FCM 웹 푸시는 각각 검증했다. iPhone과 Android에서 Google 계정 두 개로 로그인·초대·공유하고 푸시를 받는 실기기 검증과 공개 HTTPS 운영 배포는 남아 있다.
- 기존 편집 기록에서 제목과 본문의 작성자를 확인할 수 없는 일정·부탁은 원래 집에 보존한다. 계정 전체 삭제의 데이터 보관·운영 정책은 확정이 필요하다.

## 2026-11-10 MySQL 재검증

- 이 Mac에서는 Docker 테스트 실행이 여전히 막혀 있다. 대신 별도 로컬 MySQL Community Server 8.4.7에서 64건을 통과했고, 기존 동일 링크 1회 수락과 서로 다른 링크 동시 수락을 확인했다. 운영 DB와 분리된 테스트 덤프 복원 후 앱 기동도 확인했다. [검증 기록](VERIFICATION.md)

## v6.2 통합 후 남은 검증

- MySQL 8.4 테스트는 Docker 엔진이 없어 시작 실패했다. 기존 CI의 성공을 이번 변경의 성공으로 간주하지 않는다.
- 실제 iPhone Safari, 외부 HTTPS/Google 인증, Docker 이미지 빌드 및 새 배포는 미검증이다.
- 고양이는 제공 팩에 없어 기존 임시 자산을 유지한다. 사람 sit/meal 자산은 없으며 강아지 sit/sleep은 정적 자세다.
- 원본 미적 승인과 최종 자산 배포 권리 확인은 별도다.
- 새 native XY/v2 appearance를 저장한 후 구형 바이너리로 단순 롤백하면 표시 호환이 보장되지 않는다.

[V62_INTEGRATION.md](V62_INTEGRATION.md)에 현재 범위와 증거를 기록했다. 아래 기존 이력은 보존한다.


## DH-001: MySQL 초대 동시성 테스트 2건 실패 — 수정 검증 완료

- 근거: [첫 GitHub Actions 실행](https://github.com/hellomypp/dot-home/actions/runs/41309300876).
- 기존 main 18d5d8e에서 simultaneousAcceptanceUsesOneSlot 및 simultaneousUseOfSameInvitationIsSingleUse 실패.
- Google 전환 이전 오류이며 이번 OIDC 구현으로 해결됐다고 간주하지 않는다. 테스트 제외·건너뛰기 처리하지 않았다.
- 로컬 H2 테스트는 통과하지만 MySQL 검사는 Docker가 없어 재실행하지 못했다.
- 분석 후보: accept가 가구 잠금 전에 invitation을 일반 조회하고, MySQL 기본 REPEATABLE_READ에서 일관 읽기 스냅샷이 만들어진 후 잠금 대기를 거친다. 잠금 읽기와 후속 일반 count/membership 읽기 사이에 최신 상태가 달라질 가능성을 조사해야 한다. 같은 토큰 refresh만으로 모든 일관 읽기까지 갱신된다고 가정하면 안 된다. 정확한 결과값·쿼리·격리 수준을 실제 MySQL에서 재현해야 원인을 확정할 수 있다.
- 후속: 실패 시 스레드별 반환/예외와 SQL을 수집하고, 가구 인원 검사와 토큰 소비를 동일한 최신 읽기·트랜잭션 경계로 정리한 뒤 두 테스트를 MySQL에서 재검증한다.
- 영향: 여러 사용자의 동시 초대 수락 정책. 실제 가족 대상 본격 운영 전에 해결 필요.

## DH-002: Google 실제 계정 E2E 미검증

실제 Cloud Client ID/Secret 및 테스트 사용자 등록 필요. 현재 Mock 검증만 완료 대상이며 Google 실제 인증 성공으로 표시하지 않는다.


## DH-001 수정 시도 (2026-11-02)

Google 전환 후 CI 41317925677에서도 같은 두 실패가 재현됐다. accept에만 READ_COMMITTED를 적용해 가구 잠금을 기다린 뒤 count·membership 조회가 이전 수락 커밋을 읽도록 수정했다. 가구/초대 행 잠금과 토큰 refresh는 유지하고 테스트를 제외하지 않는다. 로컬 H2 회귀와 실제 MySQL CI 결과를 따로 기록한다. MySQL 확인 전에는 해결 완료로 분류하지 않는다.


### 실제 MySQL 검증 결과

[수정 브랜치 CI 41353158782](https://github.com/hellomypp/dot-home/actions/runs/41353158782)가 success로 완료됐다. 기존 실패 2건을 포함한 mysqlTest, 서버 회귀·bootJar, 프런트 테스트·빌드를 모두 통과했다. READ_COMMITTED는 초대 수락 메서드에만 적용했고 기존 가구 잠금·초대 잠금·일회성 검사·동시 수락 테스트는 유지했다.

## DH-003: v0.6 실기기·실계정 전체 E2E 미수행

쪽지/캘린더/집들이의 서버 DB·권한·HTTP/STOMP·로컬 브라우저 검사를 추가했다. 실제 iPhone Safari의 터치/키보드와 다른 Google 계정의 방문 전체 E2E는 미수행이다. 내장 브라우저 390px 검사를 실기기 통과로 보고하지 않는다. [구현/미검증 범위](V06.md). 기존 DH-001 MySQL 동시성 수정은 유지한다.

## 배포 준비: 외부 설정 대기 (2026-11-02)

Railway 웹 로그인·비용 승인·프로젝트/영구 DB·도메인·Google callback 등록이 필요하다. 공개 URL과 클라우드 실계정 E2E는 아직 없다. 앱 접근 로그는 끄지만 Railway edge HTTP 로그는 별도 수집이므로 토큰 포함 path/query의 마스킹·제외 정책을 계정에서 확인해야 한다. 공식 문서만으로는 수집 제외 설정을 확인하지 못했으며 검색 필터로 해결됐다고 처리하지 않는다. 자세한 절차와 미검증 항목은 [DEPLOYMENT](DEPLOYMENT.md).

## DH-007: 미리보기 JAR 재빌드 후 초대 생성 무응답 — 수정 (2026-11-02)

실행 중인 JVM이 `apps/server/build/libs`의 JAR를 직접 사용해, 다른 작업의 bootJar가 파일을 교체하면 이후 지연 로딩되는 클래스가 깨졌다. 실제 미리보기에서 `NoClassDefFoundError`와 초대 조회/health 요청 timeout을 재현했다. 새 격리 서버에서는 동일 초대 생성/조회/수락이 정상이라 DB 정책·초대 API 결함과 구분했다.

미리보기 시작 시 JAR를 SHA-256별 읽기 전용 `.tools/remote-preview/releases` 파일로 복사하고 launchd는 그 파일을 실행하도록 수정했다. 기존 서버를 정상 중지한 뒤 파일 DB를 비공개 백업하고 재시작했다. 다른 구성의 JAR를 다시 빌드한 뒤 HTTPS health 200/유효하지 않은 초대 410이 계속 정상임을 확인했다. 실제 iPhone에서 버튼 재확인은 사용자 확인이 필요하다.

## 가족·반려동물 정책 변경 (2026-11-02)

- 계획 인원 정책·Plan DTO/PATCH·온보딩 인원/반려동물 입력은 제거했다. 기존 ‘마지막 자리’ 검사는 서로 다른 유효 초대의 동시 수락 모두 성공으로 대체했다. 동일 토큰 경쟁·READ_COMMITTED·잠금·Membership 고유 제약은 유지한다. 과거 DH-001 이력은 이전 정책에서의 조사 기록이며 삭제하지 않는다.
- V7의 planned_people은 rollback용 잔존 컬럼이다. 새 앱에서는 참조하지 않는다. 이전 바이너리 지원 종료 후 별도 migration으로 물리 제거해야 한다. 이전 앱으로 rollback하면 예전 인원 정책이 복원되므로 초대 쓰기를 중지·검토해야 한다.
- 집별 초대 발급 빈도 제한을 2026-11-10에 적용했다. DB의 발급 시각으로 10분에 12개를 계산하며, 대표 권한·CSRF·기존 링크 수락은 유지한다.
- 실제 Google 계정 두 개와 iPhone Safari의 이번 전체 흐름은 직접 미검증이다. QA 개발 계정/내장 브라우저 및 OIDC 모킹과 구분한다.
