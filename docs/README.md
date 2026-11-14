# Carix 문서 맵

프로젝트 문서를 실제 개발 관점의 분류로 정리한 인덱스다. 정본(canonical)은
[`specs/`](specs/)와 [`plans/`](plans/)에 있다.

새 독자는 **기능 → 아키텍처 → API** 순으로 읽기를 권장한다. 각 문서 안의
"채택 사유"·"왜 이 구성인가" 절이 구조적 결정의 배경을 담고 있다.

## 기능 (Functional)

| 문서 | 내용 |
| --- | --- |
| [기능 명세서](specs/functional-specification.md) | 제품 범위·사용자 흐름·지표 규칙과 화면 맵·인수 조건·입력 검증·계산 경계·추적 매트릭스 |
| [와이어프레임 인덱스](specs/wireframes/README.md) | HTML 와이어프레임 맵 및 UI 화면 스크린샷 인덱스 |
| [화면 소개](showcase.md) | 운영 화면 캡처로 보는 기능별 화면과 사용자 유형별 판정 |

## 아키텍처 (Architecture)

| 문서 | 내용 |
| --- | --- |
| [데이터 아키텍처와 실행 흐름](specs/data-architecture.md) | 공고 5원천·CPI·임금통계 기준선·RawIngest·dedup, 서비스 흐름도와 Job 1~4 구성 |
| [데이터베이스 ERD](specs/erd.md) | 엔티티 관계도(Mermaid), 스키마 설계 주석, 운영 적용 이력 |
| [도메인 계산 정의](specs/domain-logic.md) | 지수·시나리오 계산 공식, 가중치 전파, 완화 알고리즘 |
| [프런트엔드 설계](specs/frontend-design.md) | Vite·React·Node SSR 구성, 렌더링 전략, 배포 단위, 비주얼 테마·레이아웃·시각화 컴포넌트 |
| [Spring Cloud 아키텍처](specs/spring-cloud-architecture.md) | Gateway·Config·Eureka 기반 재설계. 서비스 경계, 설정 관리, 로컬 개발 |

## API

| 문서 | 내용 |
| --- | --- |
| [REST API 규격](specs/api-specification.md) | 내부 API: 엔드포인트, 요청·응답, 인증, 에러 포맷 |
| [외부 원천 API 연동 명세](specs/external-api-integration.md) | 수집기(collector) 어댑터 경계: KOSIS 실측 명세, KOSIS 외 원천 결론·미결 |

> 실무 관점 개선 여지: 산문 규격 → OpenAPI(`docs/api/openapi.yaml`) 기계 판독본으로 승격 가능(선택).

## 리서치 (Research)

| 문서 | 내용 |
| --- | --- |
| [데이터 수집 용이성 분석](research/data-collection-feasibility.md) | CPI·채용공고 수집 난이도, 제약 요인 및 Fallback 전략 |

## 보안·운영·개발 (Ops / Dev)

| 문서 | 내용 |
| --- | --- |
| [보안·개인정보 처리](SECURITY.md) | 인증, 컬럼 암호화, 개인정보 수명주기, 로그 배제 |
| [로컬 개발 가이드](DEVELOPMENT.md) | 런타임 설치, DB 접근, dev server, 테스트 |
| [PR 자동 검증](../.github/workflows/pr-check.yml) | PR 단계 — 백엔드 전체 테스트(`./gradlew check`) 및 프런트엔드 정적 분석·단위테스트 자동 검증 |
| [배포 워크플로우](../.github/workflows/deploy.yml) | main 푸시 — ARM 러너 빌드 → GHCR → SSH → 헬스체크 및 실패 시 자동 롤백 |
| [롤백 워크플로우](../.github/workflows/rollback.yml) | 수동 롤백 — GitHub Actions UI에서 특정 SHA 또는 직전 배포본으로 1-클릭 복구 |

## 계획 (Plans)

| 문서 | 내용 |
| --- | --- |
| [개발 계획](plans/carix-dev-plan.md) | 3인 레인 분담, 주차 로드맵과 간트, 레인 간 인터페이스 계약, MVP 범위 판정 |
| [UI 일관성 통일 계획](plans/ui-unification-plan.md) | 결정 신호 단일화·토큰·공용 프리미티브 P0~P4 단계와 마이그레이션 계약 |
| [아키텍처 고도화(로드맵)](plans/architecture-roadmap.md) | MVP 이후: 캐싱, NLP 추출, 서킷 브레이커, 비동기 이벤트. 어느 것도 착수하지 않았다 |

## 진행 상태

이 절이 프로젝트 마일스톤 진행 상태(무엇이 끝났고 무엇이 남았나)를 다룬다. **배포 실행
현황도 여기 「되어 있는 것」이 갖는다.** 기술별 도입 여부는 [`README.md`의 "기술 구성"
표](../README.md#기술-구성)가, 모듈별 구현 상태는
[`backend/README.md`](../backend/README.md)가 각각 다룬다. 주제 문서는 현재 사실만
서술하고 상태를 적지 않는다.

### 되어 있는 것

| 영역 | 상태 |
| --- | --- |
| 프로젝트 정의 | 프로젝트 이름과 핵심 문제 정의 완료 |
| 지수·데이터 구조 | 핵심 지수와 데이터 연결 구조 정의 완료 |
| 저장소·협업 | GitHub 저장소 및 협업자 구성 완료 |
| MVP 요구사항 | MVP 상세 요구사항 정의 완료 (임계값·표본 기준 등 세부 수치 Config Server 반영 완료) |
| 기술 스택 | 결정 완료 |
| 데이터 수집 실측(5대 포털) | 잡코리아·사람인·원티드·점핏·프로그래머스 스크래핑 및 API 수집·적재 완료. 원천별 측정 상태는 [`verification/README.md`](../verification/README.md) 및 [외부 원천 API 연동 명세](specs/external-api-integration.md) |
| KOSIS CPI·가계동향 API 검증 | 검증 완료 — CPI(`DT_1J22001`)·가계동향(`DT_1L9U101`), 498개월 실측 명세는 [외부 원천 API 연동 명세 §2](specs/external-api-integration.md) |
| KOSIS 임금통계 기준선·시계열 | 수집 및 보간 완료 — 24계층 누적분포 보간으로 `core.wage_stat_observation`에 2020~2025년 여섯 해 `KOSIS` 실측 적재 완료. 출처 컬럼(`source`)·승격 규칙·폴백 단일 연도화·연도 시계열 읽기 경로(`DefaultWageStatSeriesProvider`, `b13`)·경력 사다리 연도 축(`b14`)·상승률 비교(`GET /dashboard/wage-growth`, `c14`) 완료. 후보 5종 비교 후 현행 기준선 유지 및 3중 안전장치 규범 확정([외부 원천 API 연동 명세 §3.3](specs/external-api-integration.md)) |
| OCI 배포 환경 | 구성 완료 — 컴포넌트별 빌드 → GHCR → SSH → 헬스체크 → 실패 시 자동 롤백으로 9개 컨테이너를 운영한다 |
| Spring Cloud 전환 | 계획 A(멀티모듈·Config Server·Eureka) 구현·운영 검증 완료, 계획 B(Gateway 도입, 요청경로 편입) 구현 완료, 계획 C(auth-service 배포 배선) 구현 완료 |
| core-api | 사용자 도메인(프로필·소득·소비·스킬)과 지수·스냅샷·시나리오가 JPA로 `core` 스키마 사용. `income_history` 는 변경 사유(`change_reason`)를 담고, 최초 소득 행 여부는 저장하지 않고 `min(effective_date)` 로 읽기 시점에 판정한다 ([ERD 스키마 설계 주석](specs/erd.md)). 고정 응답 스텁은 전부 철거했다 ([개발 계획 §4](plans/carix-dev-plan.md)). 인증은 Spring Security 대신 게이트웨이가 주입한 `X-User-Id` 를 신뢰하고 헤더가 없으면 거부한다(`@CurrentUserId` 는 401, `@RequestHeader` 를 쓰는 컨트롤러는 400). 지수 스냅샷 갱신(Job 4)은 core-api 가 전담 소유. 소득 이력은 상여 조건(포함 여부·고정상여)을, 별도 테이블은 성과급 수령 기록을 담고 프로필은 입사일을 갖는다 |
| 지수·스냅샷·시나리오 엔진 | 구매력 지수(PPI, 품목별 CPI × 소비 가중치 상속·소급 포함, 구매력 복구선 산출)·시장가치 지수(MVI, 잡코리아·원티드 무작위 표본 백필 포함)·스킬 수요 지수·수요 상승률 기준 스킬 추천(`b23`)·채용 활발도(OWI, `market_aggregate` 동료집단 채용 비중 추이·경력 2단계 완화, `b22`)·동료집단 완화(`b25`), 스냅샷 멱등 재계산과 결정 신호 판정, 시나리오 시뮬레이터 실데이터 산출이 모두 구현 완료. 시뮬레이터는 법정 퇴직금(시점별 세후 퇴직금, 4년 비교에 양쪽 퇴직금 반영, 퇴직소득세, 민법 제160조 근속 규칙)까지 낸다. 소득·소비·스킬·프로필 변경 시 재계산 트리거가 아홉 경로에 배선돼 있다. 대시보드 `summary`·`trends`·`history`·`salary-distribution`·`wage-growth` 는 실데이터로 응답한다 |
| UI 일관성 통일 | P0~P4 완료 — 결정 신호 표현 단일화, 다크 변형의 클래스 전환과 의미 색상 토큰, 공용 표현 계층(`ToneBadge`·`EmptyState`·`LoadingState`), 프리미티브 재정의와 eslint 규범. 계획은 [ui-unification-plan.md](plans/ui-unification-plan.md) |
| auth-service | 이메일·비밀번호 가입·로그인·세션 조회·로그아웃 구현 완료. 세션 토큰 해시 저장. 정리 잡이 매시 만료 세션과 끝난 속도 제한 창을 지운다. 게스트 시작(서버 게스트 계정·24시간 세션)과 가입·로그인 시 같은 `userId` 로의 정식 전환([API 규격 §2.5](specs/api-specification.md)). 매직링크·초대·메일 코드와 설정 폐기 완료 |
| collector | 모듈 추출 완료. 5대 포털(잡코리아·사람인·점핏·원티드·프로그래머스) 일괄 비동기 병렬 크롤러·중복제거·정규화·`market_aggregate` 다차원(직무×경력×지역×고용형태×회사규모) 집계(`b25`)·임금통계 수집과 파이프라인 내부 API(`/internal/pipeline/**`)·관리자 콘솔(`S-16`)·수집 데이터 탐색(`S-20`)까지 구현돼 있다. CPI 수집은 12분류와 함께 총지수를 적재한다. `@Scheduled` 정기 실행은 도입하지 않고 관리자 수동 구동으로 확정했다 ([데이터 아키텍처 §5](specs/data-architecture.md)) |
| DB 스키마 소유권 | auth 스키마는 auth-service, core 스키마는 collector(채용공고 5개 테이블·market_aggregate)와 core-api(사용자 도메인 6개 테이블·지수/시나리오)가 공유 (DDL 직접 적용) |
| 프런트엔드 | React Router + Vite 기반. ESLint/Prettier 코드 스타일 표준화, 라이트/다크 듀얼 테마, WCAG AA 접근성 준수. 테스트 스크립트(`pnpm dev`·`test`·`test:unit`·`test:e2e`) 모두 구비. 공용 계층(`coreApi` 래퍼의 봉투 해제·오류 정규화·401 단일 처리, 세션·진입 가드 loader, shadcn/ui, Zod 스키마)이 서 있다 |
| 화면 구현 | 랜딩·로그인·가입, 온보딩 5화면(1단계 입사일 필수, 프리랜서 제외), 대시보드 메인, 스킬 분석(`S-18`), 추이분석(차트 3종), 지난 판정(`S-11`), 설정·탈퇴, 이직 시뮬레이터(지금 회사·오퍼 한 화면 비교, 시점별 퇴직금 카드), 관리자 파이프라인 콘솔(`S-16`)·수집 데이터 탐색(`S-20`), 입력 관리 화면(`S-12` 소득·소비·스킬 탭)이 실제 API 에 연결돼 동작한다. 마이페이지에 상여 조건·성과급 목록·입사일, 대시보드에 성과급 포함 실소득 줄이 있다 |
| 연봉 암호화 및 복호화 경계 | KeyRing 기반 다중 키 관리·무중단 로테이션, 암호문 버전 접두사({keyId}:), 레거시 하위 호환, SensitiveDataRekeyingService 점진적 재암호화, 스냅샷 복호화 경계 및 평문 누출 차단 불변식 검증 완료 ([SECURITY §3.2](SECURITY.md)) |
| MVP 완료 기준 및 운영 검증 | 스냅샷 쓰기 경로(자문 잠금 포함), MVI `N<10` 표본 부족, Config Server 임계값 변경, 읽기 전용 커넥션 풀 분리(`b17`), 민감 금액 로깅 마스킹 점검(`a7`), 값-상태 불변식(`IndexValueContract`) 검증 완료. MVP 완료 기준 10항목은 운영 E2E(`frontend/e2e/completion-criteria.spec.ts`)와 운영 DB·로그 확인으로 전부 통과했다 ([점검 기록](../verification/notes/mvp-completion-criteria.md)) |

### 알려진 한계 및 MVP 이후 과제

MVP 기능 개발과 완료 기준 10항목 검증은 모두 끝났다. 아래는 포트폴리오 운영 환경에서 인지하고 감수한 구조적 제약 및 후속 과제다.

| 영역 | 내용 |
| --- | --- |
| 데이터 갱신 주기 | 월 갱신(지난달 표본 백필 → 재집계 → 전 사용자 재계산)은 관리자가 콘솔(`S-16`)에서 수동 실행한다 ([데이터 아키텍처 §5](specs/data-architecture.md)). 1년 넘은 채용공고는 원천에서 지워져 202606 이전 달의 MVI 는 채우지 못한다 |
| 과거 스냅샷 잔여 행 | 과거 빌드가 남긴 `opening_window_index` 가 `NULL` 인데 `rationale.indexStatuses.owi` 는 `OK` 인 행 35개(유효 3)가 DB 에 남아 있다 — 현재 코드는 `IndexValueContract` 가 쓰기 경계에서 막고, 읽기 경로는 옛 행을 `계산 대기`로 접는다 |
| 운영 보안 | collector 관리자 키(`CARIX_ADMIN_KEY`)가 운영 컨테이너에 주입되지 않으면 코드 기본값(`backend/collector/src/main/resources/application.yml`)으로 돈다 — 공개 운영 전환 시 운영 키를 주입하고 기본값을 없애야 한다. 게이트웨이 `/actuator/refresh` 가 인증 없이 열려 있다 |
| 백업·복구 및 DB 분리 | 운영 DB 백업과 암호화 키의 오프사이트 보관이 없다. 운영과 개발이 같은 DB 를 쓴다([DEVELOPMENT.md §3](DEVELOPMENT.md)) |
| 배포 게이트 | 배포 빌드가 테스트를 건너뛰고(`-x test`, PR 단계 `pr-check.yml` 에서 수행), 헬스체크(`/api/health`)는 DB·auth-service 를 보지 않는다. 자동 롤백은 `.env` 의 이미지 태그만 되돌린다 — 설정(`config/spring/`)과 DB 변경은 그대로 남는다 |
| 테스트 환경 | 백엔드 DB 테스트는 인메모리 H2(PostgreSQL 호환 모드)로 돌며 실제 PostgreSQL 로 도는 자동화 테스트는 없다 — 스키마 드리프트·자문 잠금·부분 유니크 인덱스는 운영 DB 에서 확인한다([DEVELOPMENT.md §6](DEVELOPMENT.md)). 서버의 현재 시각이 KST `Clock` 빈, UTC 현재월(`YearMonths.current()`), JVM 기본 시간대(`LocalDate.now()`)로 섞여 있다 |
| 운영 감시 | 호스트 자원은 여유가 있고 프로세스 재기동은 `restart: unless-stopped` 와 배포 헬스체크가 맡는다. 컨테이너 개별 헬스체크와 디스크 사용률 알림은 두지 않았다 |

---

문서 간 충돌 시 각 주제의 소관 문서를 따르고, 소관 문서끼리도 내용이 어긋나면
최신 커밋을 우선한다. 리뷰 라운드별 세부 수정 이력은 git 커밋 히스토리를 참조한다.
같은 사실을 두 문서에 적지 않는다. 소관 문서에만 적고 나머지는 링크한다.
추적 문서는 `docs/superpowers/`(gitignore 대상)를 링크하지 않는다.
