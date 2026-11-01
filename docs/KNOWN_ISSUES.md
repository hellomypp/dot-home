# 미해결 이슈

## DH-001: 기존 MySQL 초대 동시성 테스트 2건 실패

- 근거: [첫 GitHub Actions 실행](https://github.com/hellomypp/dot-home/actions/runs/41309300876).
- 기존 main 18d5d8e에서 simultaneousAcceptanceUsesOneSlot 및 simultaneousUseOfSameInvitationIsSingleUse 실패.
- Google 전환 이전 오류이며 이번 OIDC 구현으로 해결됐다고 간주하지 않는다. 테스트 제외·건너뛰기 처리하지 않았다.
- 로컬 H2 테스트는 통과하지만 MySQL 검사는 Docker가 없어 재실행하지 못했다.
- 분석 후보: accept가 가구 잠금 전에 invitation을 일반 조회하고, MySQL 기본 REPEATABLE_READ에서 일관 읽기 스냅샷이 만들어진 후 잠금 대기를 거친다. 잠금 읽기와 후속 일반 count/membership 읽기 사이에 최신 상태가 달라질 가능성을 조사해야 한다. 같은 토큰 refresh만으로 모든 일관 읽기까지 갱신된다고 가정하면 안 된다. 정확한 결과값·쿼리·격리 수준을 실제 MySQL에서 재현해야 원인을 확정할 수 있다.
- 후속: 실패 시 스레드별 반환/예외와 SQL을 수집하고, 가구 인원 검사와 토큰 소비를 동일한 최신 읽기·트랜잭션 경계로 정리한 뒤 두 테스트를 MySQL에서 재검증한다.
- 영향: 여러 사용자의 동시 초대 수락 정책. 실제 가족 대상 본격 운영 전에 해결 필요.

## DH-002: Google 실제 계정 E2E 미검증

실제 Cloud Client ID/Secret 및 테스트 사용자 등록 필요. 현재 Mock 검증만 완료 대상이며 Google 실제 인증 성공으로 표시하지 않는다.
