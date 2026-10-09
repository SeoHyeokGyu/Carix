# Carix 문서 맵

프로젝트 문서를 실제 개발 관점의 분류로 정리한 인덱스다. 정본(canonical)은
[`specs/`](specs/)와 [`plans/`](plans/)에 있다.

새 독자는 **제품 → 기능 → 아키텍처 → API** 순으로 읽기를 권장한다. 각 문서 안의
"채택 사유"·"왜 이 구성인가" 절이 구조적 결정의 배경을 담고 있다.

## 제품 (Product)

| 문서 | 내용 |
| --- | --- |

## 기능 (Functional)

| 문서 | 내용 |
| --- | --- |
| [기능 명세서](specs/functional-specification.md) | 제품 범위·사용자 흐름·지표 규칙과 화면 맵·인수 조건·입력 검증·계산 경계·추적 매트릭스 |
| [와이어프레임 인덱스](specs/wireframes/README.md) | HTML 와이어프레임 맵 및 UI 화면 스크린샷 인덱스 |

## 아키텍처 (Architecture)

| 문서 | 내용 |
| --- | --- |
| [데이터 아키텍처와 실행 흐름](specs/data-architecture.md) | 원천 4개·RawIngest·dedup·임금통계 기준선, 서비스 흐름도와 Job 1~4 구성 |
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
| MVP 요구사항 | MVP 상세 요구사항 정의 완료 (임계값·표본 기준 등 세부 수치는 데이터 검증 후 확정) |
| 기술 스택 | 결정 완료 |
| 데이터 수집 실측(잡코리아·사람인) | 스크래핑으로 실측 완료 — 공고 800건·스킬 699개 원격 DB 적재, 산출물은 `verification/out/` |
| KOSIS CPI·가계동향 API 검증 | 검증 완료 — CPI(`DT_1J22001`)·가계동향(`DT_1L9U101`), 498개월 실측 명세는 [외부 원천 API 연동 명세 §2](specs/external-api-integration.md) |
| KOSIS 임금통계 기준선 데이터 | 수집 및 보간 완료 — 24계층 누적분포 보간으로 `core.wage_stat_observation`에 충분한 데이터 적재 완료 |
| OCI 배포 환경 | 구성 완료 — 최소 스캐폴드 기준 빌드 → GHCR → SSH → 헬스체크 전 구간 검증 |
| Spring Cloud 전환 | 계획 A(멀티모듈·Config Server·Eureka) 구현·운영 검증 완료, 계획 B(Gateway 도입, 요청경로 편입) 구현 완료, 계획 C(auth-service 배포 배선) 구현 완료 |
| core-api | 사용자 도메인(프로필·소득·소비·스킬)과 지수·스냅샷·시나리오가 JPA로 `core` 스키마 사용. `income_history` 는 변경 사유(`change_reason`)를 담고, 최초 소득 행 여부는 저장하지 않고 `min(effective_date)` 로 읽기 시점에 판정한다 ([ERD 스키마 설계 주석](specs/erd.md)). 고정 응답 스텁은 전부 철거했다 ([개발 계획 §4](plans/carix-dev-plan.md)). 인증은 Spring Security 대신 게이트웨이가 주입한 `X-User-Id` 를 신뢰하고 헤더가 없으면 401 이다. 지수 스냅샷 갱신(Job 4)은 core-api 가 전담 소유 |
| 지수·스냅샷·시나리오 엔진 | 시장가치 지수(MVI)·스킬 수요 지수·개방창 지수(OWI)·동료집단 완화, 스냅샷 멱등 재계산과 결정 신호 판정, 시나리오 시뮬레이터 실데이터 산출이 모두 구현 완료. 소득·소비·스킬·프로필 변경 시 재계산 트리거가 여덟 경로에 배선돼 있다. 대시보드 `summary`·`trends`·`history`·`salary-distribution` 은 실데이터로 응답한다 |
| UI 일관성 통일 | P0~P4 완료 — 결정 신호 표현 단일화, 다크 변형의 클래스 전환과 의미 색상 토큰, 공용 표현 계층(`ToneBadge`·`EmptyState`·`LoadingState`), 프리미티브 재정의와 eslint 규범. 계획은 [ui-unification-plan.md](plans/ui-unification-plan.md) |
| auth-service | 이메일·비밀번호 가입·로그인·세션 조회·로그아웃 구현 완료. 세션 토큰 해시 저장. 매직링크·초대·메일 코드와 설정 폐기 완료 |
| collector | 모듈 추출 완료. 크롤러·중복제거·정규화·`market_aggregate` 집계·임금통계 수집과 파이프라인 내부 API(`/internal/pipeline/**`)까지 구현돼 있다 |
| DB 스키마 소유권 | auth 스키마는 auth-service, core 스키마는 collector(채용공고 5개 테이블·market_aggregate)와 core-api(사용자 도메인 5개 테이블·지수/시나리오)가 공유 (DDL 직접 적용) |
| 프런트엔드 | React Router + Vite 기반. ESLint/Prettier 코드 스타일 표준화, 라이트/다크 듀얼 테마, WCAG AA 접근성 준수. 테스트 스크립트(`pnpm dev`·`test`·`test:unit`·`test:e2e`) 모두 구비. 공용 계층(`coreApi` 래퍼의 봉투 해제·오류 정규화·401 단일 처리, 세션·진입 가드 loader, shadcn/ui, Zod 스키마)이 서 있다 |
| 화면 구현 | 랜딩·로그인·가입, 온보딩 5화면, 대시보드 메인, 추이분석(차트 3종), 지난 판정(`S-11`), 설정·탈퇴, 12개월 시뮬레이터, 관리자 파이프라인 콘솔, 입력 관리 화면(`S-12` 소득·소비·스킬 탭)이 실제 API 에 연결돼 동작한다 |
| 연봉 암호화 및 복호화 경계 | KeyRing 기반 다중 키 관리·무중단 로테이션, 암호문 버전 접두사({keyId}:), 레거시 하위 호환, SensitiveDataRekeyingService 점진적 재암호화, 스냅샷 복호화 경계 및 평문 누출 차단 불변식 검증 완료 ([SECURITY §3.2](SECURITY.md)) |

### 남은 것

| 영역 | 남은 작업 |
| --- | --- |
| 구매력 지수(PPI) | 품목별 CPI × 소비 가중치(상속 포함)로 개인물가를 누적하는 본 엔진이 배선돼 있다. 협상 목표의 구매력 복구선은 `c8`, 추이 화면 두 계열은 `a10` 이다 |
| A 레인 | 남은 블록은 관문 2 의 `a9`·`a10`(`US-D8`)뿐이다. 전파 점검(`a12`)·민감 금액 로깅 감사(`a7`, 완료 기준 10번)는 C 사슬에 있다 — [개발 계획 §2](plans/carix-dev-plan.md) |
| 내 스킬 시장 화면(`S-18`) | 관문 2(베타 이후). 기능 명세 인수 조건이 먼저다. `S-10` 처방/추천 블록은 `b20`에서 데이터 인과 과장 문구를 객관화하고 신호별 맥락(이직 대비·시장 수요·성장 가이드)을 정합하여 관문 1 요구를 충족했다 |
| 시장가치 지수(MVI) | 100 고정은 해소됐다(`b18`). 운영 집계는 실제 수집 월(202608~)만 남아 있고 전 사용자 스냅샷을 재계산해 MVI 는 전 구간 표본 부족이다 — 최근 3개월 대 이전 12개월 비교라 수집 이력 15개월 뒤에 값이 난다. 월별 모수는 신규 수집 공고(유량)이고 크롤링 규모 고정 및 유량 정의 정합은 완료됐다(`b21`). 개방창 지수는 게시 기간이 포털 기본값(30일·60일)에 몰려 100 근처에 붙는다 — 재정의는 `b22` |
| collector | 없음. 임금통계 적재의 출처 기록·승격 규칙·폴백 단일 연도화까지 들어왔다. 관리자 콘솔(`S-16`) UI 연동까지 완료했다. `@Scheduled` 정기 실행은 도입하지 않기로 확정했다 ([데이터 아키텍처 §5](specs/data-architecture.md)) |
| 프런트엔드 | UI 일관성 통일의 B·박지수 화면 교체가 잔여. 남은 파일은 eslint 예외 목록이 센다([ui-unification-plan.md](plans/ui-unification-plan.md) §5) |
| 검증 | 스냅샷 쓰기 경로는 자문 잠금까지 운영에서 입증됐다([도메인 정의서 §5](specs/domain-logic.md)). MVI `N<10` 표본 부족과 Config Server 임계값 변경은 단위 테스트로 검증했다. 커넥션 풀 예산은 `b17`(읽기 전용 풀 분리)로 완료됐다. 민감 금액 로깅 마스킹 점검, E2E 통합과 완료 기준 10항목 점검이 남았다 |
| 스냅샷 데이터 결함 | 없음. `market_value_index = 0` 22행은 전부 밀려난 행이고, 월별 최신 `data_version` 만 읽는 유효행에는 0 이 없다 — 원인(최근 3개월 창의 모수 0 을 수요 0 으로 읽던 것)을 고친 뒤의 실행들이 덮었다. `opening_window_index` 가 `NULL` 인데 `rationale.indexStatuses.owi` 는 `OK` 이고 근거는 다 들어있는 행 35개(유효 3)가 남아 있다 — 어느 빌드가 만들었는지 규명하지 못했고 현재 코드 경로로는 재현되지 않는다. 값-상태 불변식(`IndexValueContract`)이 산출기 계약과 쓰기 경계 두 층에서 그 모양을 막고, 읽기 경로는 옛 행을 `계산 대기`로 접는다 |
| 개방창 지수(OWI) | 없음. 공고 직접 집계·동료집단 완화·상시채용 비율 및 OWI Provider(B `b6`) 구현 완료 ([기능 명세 §4.5](specs/functional-specification.md)) |
| 지출 카테고리 Swagger 문구 | `ExpenseItem` 의 `category` 설명이 `TRANSPORTATION`·`HEALTH`·`OTHER` 를 적고 있는데 정본 `ExpenseCategoryEnum` 은 `TRANSPORT`·`MEDICAL`·`LEISURE` 다 — 세 값 모두 실존하지 않는다. 코드 주석만의 문제라 동작은 멀쩡하고 공개 API 문서만 틀렸다. 소관 A (소비 API) |
| 광역 지역 어휘 두 벌 | `core-api` `PeerGroupFallbackResolver#extractBroadRegion` 은 `서울특별시` 를 만들고 collector `RegionRollup` 은 `서울` 을 만든다. 동료집단 완화 Level 3 의 광역 조회가 `region = 'ALL'` 행으로 떨어져 증상이 가려져 있다. 값 규약 정본은 시/도 17개 한글([API 규격 §3.1](specs/api-specification.md), [ERD](specs/erd.md)). 소관 B (동료집단) |
| Gateway 세션 검증 필터 | **활성화는 끝났다** — `carix.gateway.session-validation.enabled: true` 가 저장소와 서버 체크아웃 양쪽에 있다(`a37fa47`). 남은 주의점은 되돌릴 때다 — 플래그가 `config/spring/gateway.yml` 에 있어 `.env`(이미지 태그) 롤백으로는 안 꺼진다. 전환·롤백 절차는 [`backend/README.md` 게이트웨이](../backend/README.md). 프런트엔드 소비자 전환도 끝났다(`c16`) — `dashboardMarketApi.ts`(S-11 스냅샷 이력, S-14 추이분석)가 `coreGet` 으로 갈아타 401 이 `UnauthorizedError` 로 오고, 두 화면이 `catch` 에서 `navigate(LOGIN_PATH, { replace: true })` 한다. 호출부가 loader 가 아니라 컴포넌트라 라이브러리 전환만으로는 안 됐다 |
| 임금통계 연도 시계열 | 출처 컬럼(`source`)과 **쓰기 경로가 섰다** — 수집이 연도별로 출처를 기록하고, 실측만 기존 행을 덮으며, 폴백 vintage 를 고정해 벽시계 드리프트를 막았다. 고정 이전에 쌓인 `base_period=2025` 드리프트 72행은 삭제했다([ERD 운영 스키마 적용 이력](specs/erd.md)). 운영 288행(2020~2025 여섯 해)은 재수집으로 전부 `KOSIS` 실측이다. 읽기 경로(`DefaultWageStatSeriesProvider`, `b13`) 및 경력 사다리 연도 축(`b14`)도 완료됐다. 잔여 지표는 내 상승률 vs 시장(`a9`·`c14`), 임금 vs 개인물가(`a10`) — 일정은 [개발 계획 §2](plans/carix-dev-plan.md) |
| 운영 감시 | 컨테이너 헬스체크 감시를 이 호스트의 Dozzle(8888)·Dockge(3001)에 붙이는 것과 디스크 사용률 알림이 미착수다. 루트 사용률은 정리 후 81% 이고, 공유 호스트라 스왑 4GB 가 이미 소진된 상태에서 Carix 가 올라가 있다 |

**배포 전 실행 순서는 더 없다.** 이 절이 지키던 사슬은 「검증」의 쓰기 검증 → 「Gateway 세션
검증 필터」 활성화였고, 근거는 *"플래그를 켜 놓고 돌리면 실패가 401 로 왔을 때 게이트 탓인지
`jsonb`·자문 잠금 탓인지 갈리지 않는다"* 였다. **양쪽 끝이 모두 지났다** — 필터는 `a37fa47`
로 켜졌고 서버 체크아웃도 `true` 다. 쓰기 경로는 운영 770행으로 이미 돌았다.

남은 항목들은 서로 순서 제약이 없다. 각자 소관 블록에서 친다.

---

문서 간 충돌 시 각 주제의 소관 문서를 따르고, 소관 문서끼리도 내용이 어긋나면
최신 커밋을 우선한다. 리뷰 라운드별 세부 수정 이력은 git 커밋 히스토리를 참조한다.
같은 사실을 두 문서에 적지 않는다. 소관 문서에만 적고 나머지는 링크한다.
추적 문서는 `docs/superpowers/`(gitignore 대상)를 링크하지 않는다.
