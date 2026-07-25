# Carix 문서 맵

프로젝트 문서를 실제 개발 관점의 분류로 정리한 인덱스다. 정본(canonical)은
[`specs/`](specs/)와 [`plans/`](plans/)에 있다. 최초 작성 원본은
[`superpowers/`](superpowers/README.md)에 동결 보존하며 더 이상 갱신하지 않는다.

새 독자는 **제품 → 기능 → 아키텍처 → API → 결정(ADR)** 순으로 읽기를 권장한다.

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

## 아키텍처 결정 (ADR)

| 문서 | 내용 |
| --- | --- |
| [ADR 인덱스](adr/README.md) | 되돌리기 어렵거나 재논의된 결정의 기록 (0001~0005) |

## 보안·운영·개발 (Ops / Dev)

| 문서 | 내용 |
| --- | --- |
| [보안·개인정보 처리](SECURITY.md) | 인증, 컬럼 암호화, 개인정보 수명주기, 로그 배제 |
| [로컬 개발 가이드](DEVELOPMENT.md) | 런타임 설치, OCI dev DB 터널, dev server, 테스트 |
| [CI 워크플로우](../.github/workflows/ci.yml) | 프런트엔드·백엔드 검증 (스캐폴드 후 활성) |

## 계획 (Plans)

| 문서 | 내용 |
| --- | --- |
| [데이터 선행 검증](plans/data-verification-plan.md) | 원천 4개 API 실측·약관·표본 기준 산출 (구현 착수 선결) |
| [OCI 배포 계획](plans/oci-deployment-plan.md) | OCI 배포 환경 구성 |

---

문서 간 충돌 시 각 주제의 소관 문서와 [ADR](adr/README.md)을 우선한다. 리뷰 라운드별
세부 수정 이력은 git 커밋 히스토리를 참조한다.
