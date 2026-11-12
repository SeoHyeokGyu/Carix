# MVP 완료 기준 10항목 점검 (2026-11-12)

기준표는 [기능 명세 §7](../../docs/specs/functional-specification.md)이다. 운영
(`carix.144.24.90.88.sslip.io`)에서 확인했다. 배포 이미지는 core-api `84a0c5ad`, frontend `22191933`
(main `aa6b2f95` 와 코드가 같다 — 그 뒤 머지는 문서뿐이다).

## 실행

- E2E: `frontend/e2e/completion-criteria.spec.ts`. 운영에 새 정식 계정을 만들어 가입부터
  탈퇴까지 한 번에 돌고, 탈퇴까지 마친다. `CARIX_E2E_CRITERIA=1` 이 있을 때만 돈다.
  운영에서는 baseURL 을 운영 도메인으로 둔 임시 config 로 돌린다(`webServer` 없음).
  2026-11-12 두 번 통과(11.8초·9.9초, 두 번째는 머리말 기준월 단언 추가 뒤), 테스트 계정 userId
  `b9e6c78d-…`·`241821a9-…` — 둘 다 탈퇴 뒤 core·auth 행 0.
- 운영 DB·로그 확인: `ssh oci` 로 `carix-postgres-1`(읽기 전용 쿼리), 각 컨테이너
  `docker logs --since 40m`.

## 결과

| # | 기준 | 판정 | 근거 |
| --- | --- | --- | --- |
| 1 | 가입·로그아웃·재로그인 | 통과 | E2E: `/register` 가입 → 계정 메뉴 로그아웃 → 재로그인 시 `/dashboard` |
| 2 | 온보딩 중단·재개 | 통과 | E2E: 소득까지 입력하고 새 브라우저 컨텍스트에서 로그인하면 `/onboarding/expense` 로 돌아온다 |
| 3 | 계산 근거·데이터 기준일 | 통과 | E2E: 대시보드 머리말 `YYYY년 M월 기준`(늘 보임), 신뢰 층 「근거 펼치기」 뒤 `기준일 YYYY-MM-DD`·`규칙 버전 x.y.z` |
| 4 | 사용자 입력·기본값 추정·표본 부족 구분 | 통과 | E2E: 소비를 비우면 구매력 카드에 `통계청 평균 기준`. 표본 부족은 운영 데이터로 강제할 수 없어 `indexStatus.test.ts`(라벨)와 `IndexCard` 의 상태 뱃지 경로로 본다 |
| 5 | 결정 신호·적용 규칙 | 통과 | E2E: 판정 히어로 `적용 규칙` 목록에 항목 1개 이상 |
| 6 | 잔류·이직 시나리오 비교 | 통과 | E2E: 오퍼 연봉·실업급여 입력 뒤 `4년 누적 차이`·`따라잡는 시점` |
| 7 | 월별 스냅샷 중복 없음 | 통과 | 스키마 `UNIQUE (user_id, base_year_month, data_version, rule_version)`. 운영 중복 튜플 0 |
| 8 | 외부 데이터 지연·마지막 성공 기준일 | 통과(테스트 근거) | 운영 데이터로 지연을 만들 수 없다. `SnapshotRecalculationServiceTest`·`DefaultPurchasingPowerIndexProviderTest`·`SnapshotReadServiceTest`(백엔드 `UPDATE_DELAYED`), `indexStatus.test.ts`(화면 라벨) |
| 9 | 사용자 정보·개인 데이터 삭제 | 통과 | 탈퇴 직후 행 0(아래 세부). 「발견」 1 은 PR #381 `AuthCleanupJob` 으로 닫았다 |
| 10 | 금액이 로그·분석 이벤트에 남지 않음 | 통과 | 로그: 실행 구간(탈퇴 로그 10:00:56 UTC 포함)의 core-api·auth-service·gateway·collector 로그에 입력 금액(`6543`·`65430000`·`4520000`·`74000000`)과 테스트 이메일 0건. frontend-node·caddy 는 그 구간 표준 출력 로그가 0줄이다. 분석 이벤트: 프런트엔드에 분석 SDK 가 없어 해당 없음 |

### 9 의 세부

- 통과한 것: 탈퇴 직후 그 userId 의 core 7개 테이블(`career_profile`·`income_history`·
  `performance_bonus`·`expense_history`·`user_skill`·`index_snapshot`·`career_scenario`)과
  `auth.users`·`auth.session` 이 전부 0행이고, 고아 `expense_item` 도 0행이다(`ON DELETE CASCADE`).
  같은 자격 증명으로 다시 로그인하면 `401` 이다. 운영 카탈로그(`information_schema.columns`, core·auth)에서
  `user`·`email`·`ip`·`key`·`owner` 가 든 컬럼은 core 7개 테이블의 `user_id`, `auth.session.user_id`,
  `auth.users.email`, `auth.rate_limit_bucket.bucket_key` 뿐이고, 앞의 아홉은 `UserDataEraser` 의 삭제
  범위에 들어 있다(시나리오 초안은 `career_scenario.draft` 행).
- 게스트 정리: 만료 게스트(생성 25시간 초과) 0명, 2026-11-02 만료 예정이던 게스트 `b247560f…` 의
  auth·core 행 0. `GuestCleanupJob` 이 매시 돈다(10:00 로그 확인).

## 발견

1. **`auth.rate_limit_bucket` 에 이메일이 평문으로 남고 지워지지 않는다.** 로그인 속도 제한
   키가 `login-email:<정규화 이메일>` 이고(`AccountController`), 탈퇴(`UserDataEraser.erase`)도
   게스트 정리도 이 테이블을 건드리지 않으며, 만료 창을 지우는 경로도 없다. 운영 92행, 가장
   오래된 창 2026-08-27. 살아 있는 계정이 없는 이메일 키가 5개다(이번 테스트 계정 포함). 기준 9 「개인 데이터를 삭제할 수 있다」에 걸린다.
   탈퇴 때 그 키를 지우는 것으로는 닫히지 않는다 — 탈퇴 뒤 같은 이메일로 로그인을 한 번만
   시도해도 키가 다시 생긴다. 만료 창(1시간) 정리를
   두거나(IP 키도 함께 줄어든다) 키를 이메일 해시로 바꿔야 한다.

   **해소 (2026-11-12, PR #381):** `AuthCleanupJob` 이 매시 5분에 끝난 창과 만료 세션을 지운다. 배포 뒤 첫 실행
   (11:05 UTC)이 속도 제한 93행·만료 세션 17행을 지웠고, 11:06 에 남은 행은 버킷 0·만료 세션 0, 계정 없는
   이메일 키 0 이다.
