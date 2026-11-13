# Carix 보안·개인정보 처리

Carix는 연봉·실수령액·소비 금액 등 민감한 개인 재무 정보를 다룬다. 이 문서는 인증,
데이터 보호, 개인정보 수명주기 정책을 한곳에 모은다. 세부 규격은 각 설계 문서를
참조하되, 정책 기준은 이 문서를 따른다.

관련: [ERD](specs/erd.md), [도메인 정의서](specs/domain-logic.md),
[기능 명세서](specs/functional-specification.md).

## 1. 인증

초기 API 규격은 HTTP-only 쿠키, bearer `Authorization` 헤더, 상태ful `session` 테이블을
동시에 언급하며 우선순위·포맷을 정하지 않아 클라이언트·서버 구현이 갈릴 위험이 있었다.
**서버 세션 단일 방식**으로 통일해 정리했다.

- **정식 계정은 이메일·비밀번호로만 만든다.** 가입하면 곧바로 세션을 발급해 온보딩으로 보낸다.
- **게스트는 랜딩 버튼으로만 만든다.** 이메일·비밀번호가 없는 사용자 행과 24시간 세션
  (연장 없음)을 받는다. 방문만으로는 만들지 않는다. 게스트 쿠키로 가입하면 새 행을 만들지
  않고 같은 사용자를 정식 계정으로 전환하며, 그 사용자의 세션을 전부 폐기하고 30일 세션을
  다시 발급한다. 게이트웨이 30초 캐시 동안 폐기된 토큰이 통하는 창은 로그아웃과 같다.
  다른 계정으로 로그인하면 쓰던 게스트의 세션을 폐기한다.
- 비밀번호: BCrypt 해시로만 저장한다. 최소 8자를 요구하고 문자 구성(대소문자·특수문자)은
  강제하지 않는다 — 구성 규칙은 사용자를 예측 가능한 패턴으로 밀어넣는다.
- 세션: 불투명 토큰을 HTTP-only + Secure 쿠키에 담고 서버 `session` 테이블에서
  검증한다. **원본 토큰은 저장하지 않고 암호학적 해시만 저장한다** — 세션은 30일짜리
  자격증명(게스트는 24시간)이라 DB 유출 시 피해가 가장 크다. JWT·`Authorization` 헤더는 쓰지 않는다.
- 개별 로그아웃과 전체 기기 로그아웃(전 세션 폐기)을 제공한다.

**비밀번호 재설정은 MVP 범위 밖이다.** 이메일 발송 경로를 두지 않으므로 재설정 링크를
보낼 수단이 없다. 비밀번호를 잊으면 다른 이메일로 새 계정을 만든다. 이메일 소유
증명도 하지 않는다 — 사용자 실체가 본인과 방문자뿐이라 사칭 동기가 없고, 증명을
하려면 이메일 발송이 다시 필요해진다.

**JWT 무상태 세션은 보안 및 즉시 폐기 한계로 인해 배제하고 서버 세션을 채택했다** — 즉시 폐기(전체 로그아웃·강제 만료)가 어렵고,
민감 재무 데이터를 다루는 앱에서 토큰 유출 대응이 복잡해집니다.

## 2. 계정 열거·남용 방지

- **로그인 실패는 하나의 응답으로 통일한다.** 없는 이메일과 비밀번호 불일치를 구분하지
  않는다. 구분하면 이메일 존재 여부가 응답으로 드러난다.
- **계정 등록에서는 이메일 중복을 알려준다.** 여기서 숨기면 사용자가 "가입이 안 되는데
  이유를 모르는" 상태가 된다. 대신 등록 요청에 IP 속도 제한을 걸어 이메일 목록을
  긁는 것을 막는다.
- 속도 제한:

  | 대상 | 제한 |
  | --- | --- |
  | 가입 | IP당 20회/시간 |
  | 게스트 시작 | IP당 20회/시간 |
  | 로그인 | 이메일당 10회/시간, IP당 30회/시간 |

  로그인에 이메일 기준 제한을 함께 거는 것이 핵심이다. IP만 걸면 분산 대입에 뚫린다.

## 3. 저장 데이터 보호

### 3.1 컬럼 레벨 암호화

- 대상: `income_history.gross_salary_encrypted`, `income_history.net_salary_encrypted`,
  `income_history.fixed_bonus_annual_encrypted`, `performance_bonus.amount_encrypted`,
  `expense_item.amount_encrypted`, 그리고 `career_scenario` 의 금액 컬럼 여섯
  (`current_gross_salary_encrypted`, `move_target_gross_salary_encrypted`,
  `stay_compensation_12m_encrypted`, `move_compensation_12m_encrypted`,
  `estimated_unemployment_benefit_encrypted`, `difference_encrypted`), 그리고 세후 평가로 저장한
  시나리오의 요청·결과 JSON `career_scenario.payload_encrypted`(문자열 컨버터, 같은 키·포맷).
  엔진 1 컬럼과 payload 는 행 종류에 따라 한쪽만 채워지고 비는 컬럼은 `NULL` 이다.
- `career_scenario` 를 포함하는 이유는 파생값도 원본을 되돌리기 때문이다.
  `stay_compensation_12m = 현재연봉 × (1 + stay_expected_raise_rate/100)` 이므로 두 값이
  평문이면 나눗셈 한 번으로 연봉이 원 단위까지 복원된다. 금액이 아닌 컬럼
  (`move_one_time_cost`, `stay_expected_raise_rate`)은 평문으로 둔다.
- 알고리즘: AES-256-GCM (또는 검증된 AES-256-CBC).
- 애플리케이션 레이어(Spring Data JPA `AttributeConverter`)에서 적재 시 암호화,
  조회 시 복호화한다. 평문은 DB 버퍼·파일시스템에 그대로 남기지 않는다.

### 3.2 키 관리 및 로테이션 (확정)

- **키 주입 및 키 링(Key Ring)**: 키는 환경변수로 주입하며 저장소·로그에 절대 커밋하지 않는다.
  - 단일 키: `CARIX_ENCRYPTION_KEY` (자동으로 식별자 `v1` 할당).
  - 다중 키(로테이션): `CARIX_ENCRYPTION_KEYS=v1:<key1>,v2:<key2>` 및 `CARIX_ENCRYPTION_ACTIVE_KEY_ID=v2`.
- **암호문 포맷 및 버전 관리**:
  - 신규 암호문: `{keyId}:{Base64(IV + Ciphertext + Tag)}` (예: `v2:...`).
  - 하위 호환: 콜론(`:`) 접두사가 없는 레거시 암호문은 레거시 키(`v1`)로 자동 복호화.
- **무중단 키 로테이션 및 재암호화 절차**:
  1. `CARIX_ENCRYPTION_KEYS`에 신규 키를 추가하고 `CARIX_ENCRYPTION_ACTIVE_KEY_ID`를 신규 키로 지정해 배포한다 (신규 쓰기는 즉시 신규 키로 암호화, 구 암호문은 이전 키로 정상 복호화).
  2. `CARIX_ENCRYPTION_REKEY_ON_STARTUP=true` 로 core-api 를 재기동한다. 기동이 끝난 뒤 `SensitiveDataRekeyingService.rekeyAll()` 이 `income_history`, `expense_item`, `career_scenario`(금액 컬럼과 `payload_encrypted`), `performance_bonus` 의 구 암호문(접두사 없는 레거시 포함)을 활성 키로 재암호화한다. 이미 활성 키로 된 행은 건너뛰므로 플래그를 켜 둔 채 재기동해도 비용이 거의 없다.
  3. core-api 는 기동할 때마다 `rekey-status activeKeyId=… incomeHistory=… expenseItem=… careerScenario=… performanceBonus=… fullyMigrated=…` 한 줄을 남긴다. `fullyMigrated=true`(잔여 0건)를 확인한 뒤 구 키를 환경변수에서 폐기하고 플래그를 끈다.
  - 재암호화는 서비스가 요청을 받는 동안 돈다. 행마다 읽은 암호문이 그대로일 때만 갱신해 그사이 사용자가 고친 값을 옛 값으로 덮어쓰지 않는다.
  - 재암호화나 상태 조회가 실패해도 기동을 막지 않는다. `rekey-failed`·`rekey-status-failed` 로그와 미전환 건수로 드러난다. 로그에는 키 ID·건수·예외 문구만 남고 금액과 키 값은 남지 않는다.
  - 키는 서버 `.env` 한 곳에만 있다(백업 없음). 키를 추가·폐기하기 전에 분실 시 그 키의 암호문은 복구되지 않는다는 점을 확인한다.
- **스냅샷 복호화 경계 (Decryption Boundary)**:
  - 연봉 복호화는 오직 소득 도메인(`CurrentSalaryProvider` - `IncomeCurrentSalaryProvider`) 내부에서만 격리 수행된다.
  - 스냅샷 엔진(`SnapshotRecalculationService`)은 암호화 컬럼에 직접 접근하지 않으며, 전달받은 `CurrentSalary`는 루프 내에서 결정 신호 판정에만 사용되고 즉시 메모리에서 폐기된다.
  - `index_snapshot` 테이블 및 `rationale` JSONB에는 평문 연봉이나 역산 가능한 연속형 격차 비율을 일절 저장하지 않으며, 오직 `SalaryGapBucket` 구간명만 영속화한다 (`SnapshotDecryptionBoundarySecurityTest`로 누출 방지 불변식 보증).
  - `CurrentSalary.toString()`은 금액을 마스킹(`grossSalary=***`)하여 로그 출력을 원천 방어한다.
  - 상승률 비교(`US-D8`)는 같은 경계의 `CurrentSalaryProvider.recordedYearIncomes` 로 연도별 연 소득을 받는다. `RecordedYearIncome`·`WageGrowthResponse.Point` 도 `toString()`에서 금액을 가리고, 값은 응답으로만 나가며 저장하지 않는다.
  - 시나리오 사후 검증(`c21`)은 같은 경계의 `CurrentSalaryProvider.recordedIncomeChanges` 로 소득 행을 받는다. `RecordedIncomeChange`·`ScenarioVerification` 의 `toString` 은 금액을 가리고, 비교 결과는 본인 응답으로만 나가며 저장하지 않는다.

> **세션 검증 필터링**: 세션 검증은 Spring Cloud Gateway 필터가 수행하며, 하류 서비스는 `X-User-Id` 헤더를 신뢰합니다 ([Spring Cloud 아키텍처](specs/spring-cloud-architecture.md) 참조).
> 헤더를 신뢰하는 구조이므로 게이트웨이는 인바운드 `X-User-Id` 를 전 라우트에서 무조건 벗깁니다 — 세션 검증 플래그가 꺼져 있어도 동작하며, 그래야 플래그가 꺼진 기간에 임의 UUID 로 다른 사용자를 사칭할 수 없습니다.

### 3.3 전송·인프라

- 모든 통신은 HTTPS. 서버의 라우터 Caddy(`infra_caddy`)에서 TLS 종료하며 인증서는
  sslip.io + 자동 ACME로 발급·갱신한다. 프로젝트 전용 `carix_caddy`는 `auto_https off`로
  내부 평문 :80만 받고 인증서를 다루지 않는다. **인증서 관리 지점을 하나로 모으기 위한 구조**이며,
  공유 호스트에서 80/443을 한 컨테이너만 바인딩할 수 있다는 제약에서 나왔다.
- 운영 DB는 OCI VM PostgreSQL 컨테이너로 운영하며 볼륨 암호화를 함께 한다
  (서버 전용 근거는 README.md 채택 사유). 외부 이미지는 버전이 고정돼 있지 않다 —
  `postgres:16-alpine`·`caddy:2-alpine` 은 움직이는 태그라 배포 때 pull 하면 바뀔 수 있다.
- **운영과 개발이 같은 DB를 쓴다.** 따라서 팀원은 실사용자 데이터에 직접 접근한다.
  감수한 위험은 [`DEVELOPMENT.md`](DEVELOPMENT.md) §3 참조.
- **접근 경로: TLS + OCI Security List IP 허용목록.** Postgres 컨테이너 포트는
  `0.0.0.0:5432`로 발행하되, Docker 발행 포트는 호스트 firewalld를 우회하므로(DOCKER
  체인) 호스트 방화벽으로는 못 막는다 — 실제 접근 제어는 호스트 밖 **OCI VCN Security
  List**에서 한다(TCP 5432, 팀원 IP `/32`만 개별 허용, default-deny). Security List 는
  Docker 의 iptables 조작보다 앞단이라 우회되지 않는다.
- **PostgreSQL TLS를 강제한다.** 컨테이너 내부망(Docker 브리지, 172.16.0.0/12)은 평문
  허용하고 그 외(=인터넷 경유 외부 접속)는 `hostssl`로 TLS를 강제한다
  (`deploy/postgres/pg_hba.conf`). Security List 규칙이 실수로 넓어지더라도 TLS 없이는
  붙을 수 없는 이중 방어다. 자체 서명 인증서라 클라이언트는 `sslmode=require`를 쓴다
  (`verify-full`은 인증서 체인 검증에 실패한다).
- DB 인증은 **개인별 롤**을 쓴다. 공용 계정을 돌려쓰지 않는다 — 이탈 시 회수가 불가능하다
  ([DEVELOPMENT.md §3.0](DEVELOPMENT.md)).

## 4. 로그·관측성에서의 민감정보 배제

- 연봉·실수령액·소비 금액을 애플리케이션 로그·분석 이벤트에 남기지 않는다.
- 작업 전달·로그에는 이메일 대신 내부 사용자 ID를 사용한다.
- 로그에는 오류 코드만 남기고 민감 입력을 제외한다. 요청 ID(`X-Request-Id`)는 게이트웨이가 만들어
  하류 요청과 응답에 싣지만, 하류 서비스 로그에는 남기지 않는다
  ([Spring Cloud 아키텍처 §4](specs/spring-cloud-architecture.md)).
- 오류 응답에 내부 코드·스택·내부 URL을 노출하지 않는다.
- MVP 운영 도구에서 사용자별 민감 금액을 조회하지 않는다.

## 5. 개인정보 수명주기

- 최소 수집: 지수 계산에 필요한 범위만 수집한다.
- 사용자 및 개인 데이터 삭제 시 프로필, 개인 입력 이력(소득·소비·스킬), 스냅샷, 시나리오,
  세션, 사용자 행을 삭제한다. 삭제 전 범위를 고지하고 이메일 재입력으로 확인받는다.
- 공용 CPI·채용시장 집계 데이터는 개인 데이터와 논리적으로 분리하며, 집계에 사용자
  이메일을 포함하지 않는다. 사용자 정보 삭제는 공용 집계에 영향을 주지 않는다.
- 게스트 데이터는 세션 만료(발급 후 24시간) 뒤 정리 작업이 사용자 삭제와 같은 범위로 지운다.
  정리는 게스트 행 삭제를 먼저 시도해 그사이 정식 계정으로 전환된 사용자는 지우지 않는다.
- `raw_ingest`는 원문을 보관하지 않고 수집 이력(원천·원문 식별자·수집 시각)만 남긴다.
  보관할 원문이 없으므로 별도 보존 기간을 두지 않는다.

## 6. 입력 검증

- 시스템 경계에서 모든 사용자 입력을 검증한다. 허용 범위는
  [기능 분해 명세 §3](specs/functional-specification.md)을 따른다.
- 음수·범위 초과 금액, 미래 시점 이력, 중복 유효일 소득, 중복 스킬을 거부한다.
- 파라미터화 쿼리·ORM으로 SQL 인젝션을 방지하고, 프런트엔드 출력은 이스케이프한다.

## 7. 취약점 신고

저장소가 PRIVATE인 동안은 협업자가 이슈로 보고한다. 공개 전환 시 신고 채널과
대응 정책을 이 절에 확정한다.
