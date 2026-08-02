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

## 아키텍처 (Architecture)

| 문서 | 내용 |
| --- | --- |
| [파이프라인 재설계](specs/data-architecture.md) | 데이터 아키텍처: 원천 4개, RawIngest, dedup, 임금통계 기준선 |
| [데이터베이스 ERD](specs/erd.md) | 데이터베이스 엔티티 관계도 (Mermaid) |
| [애플리케이션 워크플로우](specs/application-workflow.md) | E2E 데이터 흐름, Spring Batch Job 파이프라인, 시퀀스 |
| [상세 기술 명세](specs/technical-specification.md) | DB 스키마(DDL), 결정 규칙표, 스킬 사전, 외부 API 연동 |
| [도메인 계산 정의](specs/domain-logic.md) | 지수·시나리오 계산 공식, 가중치 전파, 완화 알고리즘 |
| [프런트엔드 설계](specs/frontend-design.md) | Vite·React·Node SSR 구성, 렌더링 전략, 배포 단위 |
| [아키텍처 고도화(로드맵)](specs/architecture-roadmap.md) | MVP 이후: 캐싱, NLP 추출, 서킷 브레이커, 비동기 이벤트 |
| [Spring Cloud 아키텍처](specs/spring-cloud-architecture.md) | Gateway·Config·Eureka 기반 재설계. 서비스 경계, 설정 관리, 로컬 개발 (계획 A 구현·검증 완료, B·C 미구현) |

## API

| 문서 | 내용 |
| --- | --- |
| [REST API 규격](specs/api-specification.md) | 내부 API: 엔드포인트, 요청·응답, 인증, 에러 포맷 |
| [외부 원천 API 연동 명세](specs/external-api-integration.md) | 배치 어댑터 경계: KOSIS 실측 명세, 미검증 원천 확인 항목 |

> 실무 관점 개선 여지: 산문 규격 → OpenAPI(`docs/api/openapi.yaml`) 기계 판독본으로 승격 가능(선택).

## 리서치 (Research)

| 문서 | 내용 |
| --- | --- |
| [데이터 수집 타당성 분석](research/data-collection-feasibility.md) | 원천별 수집 용이성, 결측·편향, 대응 전략 (분석 노트) |

## 보안·운영·개발 (Ops / Dev)

| 문서 | 내용 |
| --- | --- |
| [보안·개인정보 처리](SECURITY.md) | 인증, 컬럼 암호화, 개인정보 수명주기, 로그 배제 |
| [로컬 개발 가이드](DEVELOPMENT.md) | 런타임 설치, DB 접근, dev server, 테스트 |
| [배포 워크플로우](../.github/workflows/deploy.yml) | main 푸시 — ARM 러너 테스트·빌드 → GHCR → SSH → 헬스체크 |

> **PR 단계 CI는 없다.** 자동 검증은 `deploy.yml`의 backend 잡(`./gradlew build`)이 main 푸시에서
> 수행하는 것이 전부다. 프런트엔드는 `docker build`만 거치므로 lint·typecheck·test가 자동으로는
> 돌지 않는다 — 로컬에서 직접 돌린다.

## 계획 (Plans)

| 문서 | 내용 |
| --- | --- |
| [데이터 선행 검증](plans/data-verification-plan.md) | 원천 4개 API 실측·약관·표본 기준 산출 (구현 착수 선결) |
| [OCI 서버 구성·배포](plans/oci-server-provisioning-plan.md) | 정본. 디스크·엣지·서버 준비·CD 워크플로·첫 배포 실측과 장애 기록 |
| [Spring Cloud 전환 A](plans/spring-cloud-a-infra.md) | 멀티모듈·Eureka·Config Server. 구현·운영 검증 완료(2026-07-30). 실측으로 드러난 계획 결함 3건 포함. Gateway(B)·auth 분리(C)는 이후 |
| [OCI 배포 계획](plans/oci-deployment-plan.md) | 초기 문서화 작업 기록 (완료·대체됨). 배포 실무는 위 문서를 본다 |

---

문서 간 충돌 시 각 주제의 소관 문서를 따르고, 소관 문서끼리도 내용이 어긋나면
최신 커밋을 우선한다. 리뷰 라운드별 세부 수정 이력은 git 커밋 히스토리를 참조한다.
