# Carix 문서 맵

프로젝트 문서를 실제 개발 관점의 분류로 정리한 인덱스다. 정본(canonical)은
[`specs/`](specs/)와 [`plans/`](plans/)에 있다.

새 독자는 **제품 → 기능 → 아키텍처 → API** 순으로 읽기를 권장한다. 각 문서 안의
"채택 사유"·"왜 이 구성인가" 절이 구조적 결정의 배경을 담고 있다.

## 제품 (Product)

| 문서 | 내용 |
| --- | --- |
| [MVP 설계](specs/mvp-requirements.md) | 제품 목적, MVP 범위, 사용자 흐름, 지표·규칙, 완료 기준 |

## 기능 (Functional)

| 문서 | 내용 |
| --- | --- |
| [기능 명세서](specs/functional-specification.md) | 화면 맵, 사용자 스토리·인수 조건, 입력 검증, 계산 경계, 추적 매트릭스 |
| [UI/UX 레이아웃 설계](specs/ui-ux-design.md) | 비주얼 테마, 대시보드·온보딩 레이아웃, 컴포넌트 |
| [와이어프레임 인덱스](specs/wireframes/README.md) | HTML 와이어프레임 맵 및 UI 화면 스크린샷 인덱스 |

## 아키텍처 (Architecture)

| 문서 | 내용 |
| --- | --- |
| [파이프라인 재설계](specs/data-architecture.md) | 데이터 아키텍처: 원천 4개, RawIngest, dedup, 임금통계 기준선 |
| [데이터베이스 ERD](specs/erd.md) | 데이터베이스 엔티티 관계도 (Mermaid) |
| [애플리케이션 워크플로우](specs/application-workflow.md) | E2E 데이터 흐름, 관리자 직접 구동 및 Spring Scheduler 수집/집계 파이프라인, 시퀀스 |
| [상세 기술 명세](specs/technical-specification.md) | DB 스키마(DDL), 결정 규칙표, 스킬 사전 |
| [도메인 계산 정의](specs/domain-logic.md) | 지수·시나리오 계산 공식, 가중치 전파, 완화 알고리즘 |
| [프런트엔드 설계](specs/frontend-design.md) | Vite·React·Node SSR 구성, 렌더링 전략, 배포 단위 |
| [아키텍처 고도화(로드맵)](specs/architecture-roadmap.md) | MVP 이후: 캐싱, NLP 추출, 서킷 브레이커, 비동기 이벤트 |
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
| [OCI 서버 구성·배포](plans/oci-server-provisioning-plan.md) | 정본. 디스크·엣지·서버 준비·CD 워크플로·첫 배포 실측과 장애 기록 |

## 진행 상태

이 절이 프로젝트 마일스톤 진행 상태(무엇이 끝났고 무엇이 남았나)를 다룬다. 기술별
도입 여부는 [`README.md`의 "기술 구성" 표](../README.md#기술-구성)가, 모듈별 구현
상태는 [`backend/README.md`](../backend/README.md)가, 배포 실행 현황은
[OCI 서버 구성·배포 "실행 현황"](plans/oci-server-provisioning-plan.md#실행-현황)이
각각 다룬다. 주제 문서는 현재 사실만 서술하고 상태를 적지 않는다.

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
| core-api | 사용자 도메인(프로필·소득·소비·스킬)과 지수·스냅샷·시나리오가 JPA로 `core` 스키마 사용. 고정 응답 스텁은 전부 철거했다 ([개발 계획 §5-G](plans/carix-dev-plan.md)). 인증은 Spring Security 대신 게이트웨이가 주입한 `X-User-Id` 를 신뢰하고 헤더가 없으면 401 이다. 지수 스냅샷 갱신(Job 4)은 core-api 가 전담 소유 |
| auth-service | 이메일·비밀번호 가입·로그인·세션 조회·로그아웃 구현 완료. 세션 토큰 해시 저장. 매직링크·초대·메일 코드와 설정 폐기 완료 |
| collector | 모듈 추출 완료. 크롤러·중복제거·정규화·`market_aggregate` 집계·임금통계 수집과 파이프라인 내부 API(`/internal/pipeline/**`)까지 구현돼 있다 |
| DB 스키마 소유권 | auth 스키마는 auth-service, core 스키마는 collector(채용공고 5개 테이블·market_aggregate)와 core-api(사용자 도메인 5개 테이블·지수/시나리오)가 공유 (DDL 직접 적용) |
| 프런트엔드 | React Router + Vite 기반. ESLint/Prettier 코드 스타일 표준화, 라이트/다크 듀얼 테마, WCAG AA 접근성 준수. 테스트 스크립트(`pnpm dev`·`test`·`test:unit`·`test:e2e`) 모두 구비. MVP 전 화면 라우트·레이아웃·진입 가드 완료. 랜딩·로그인·회원가입은 실제 API에 연결돼 동작 |

### 남은 것

| 영역 | 남은 작업 |
| --- | --- |
| 애플리케이션 구현 | 온보딩·대시보드·추이분석·시뮬레이터·마이페이지 화면 UI (라우트와 가드는 완료, 화면은 플레이스홀더). 구매력 지수(PPI) 계산 엔진. 사용자 도메인 CRUD·MVI·스냅샷 갱신 엔진(`recalculateSnapshot`)·시나리오 계산은 구현 완료 |
| collector | 없음. 관리자 콘솔(`S-16`) UI 연동까지 완료했다. `@Scheduled` 정기 실행은 도입하지 않기로 확정했다 ([개발 계획 §5-C](plans/carix-dev-plan.md)) |
| 프런트엔드 | 온보딩 5단계·대시보드·추이분석·시뮬레이터·마이페이지 화면 구현. 라우트·가드·세션 연결은 완료 |
| Gateway 세션 검증 필터 | `carix.gateway.session-validation.enabled` 활성화 — 기본값 `false`. **`/api/health` 는 필터가 붙지 않는 `core-health` 라우트(`order: -15`)로 이미 분리돼 있어** 배포 헬스체크는 401 을 받지 않는다. 남은 주의점은 되돌릴 때다 — 플래그가 `config/spring/gateway.yml` 에 있어 `.env`(이미지 태그) 롤백으로는 안 꺼진다. 전환·롤백 절차는 [개발 계획 §5-B](plans/carix-dev-plan.md) |

---

문서 간 충돌 시 각 주제의 소관 문서를 따르고, 소관 문서끼리도 내용이 어긋나면
최신 커밋을 우선한다. 리뷰 라운드별 세부 수정 이력은 git 커밋 히스토리를 참조한다.
같은 사실을 두 문서에 적지 않는다. 소관 문서에만 적고 나머지는 링크한다.
추적 문서는 `docs/superpowers/`(gitignore 대상)를 링크하지 않는다.
