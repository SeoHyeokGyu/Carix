# Architecture Decision Records (ADR)

되돌리기 어렵거나 자주 재논의되는 결정을 기록한다. 각 ADR은 결정 시점의 맥락을
보존해, 이후 "왜 이렇게 정했나"를 재추적할 수 있게 한다.

## 형식

`NNNN-kebab-title.md`, 상태는 `제안` / `채택` / `폐기` / `대체됨`. 대체 시 새 ADR을
쓰고 이전 것의 상태를 갱신한다. 기존 결정을 편집하지 않는다.

## 목록

| # | 제목 | 상태 |
| --- | --- | --- |
| [0001](0001-hybrid-design-lineage.md) | 설계 계보 하이브리드 (제품 7/18 · 데이터 7/15) | 채택 |
| [0002](0002-frontend-vite-react-node-ssr.md) | 프런트엔드 Vite + React + Node SSR (Next.js 대체) | 채택 |
| [0003](0003-docker-db-oci-only.md) | Docker·PostgreSQL은 OCI 서버 전용 | 채택 (dev/운영 DB 분리 항목은 [0007]로 대체) |
| [0004](0004-salary-baseline-wage-stat.md) | 연봉 격차 기준선을 공고 공개연봉 → 공식 임금통계 | 채택 |
| [0005](0005-server-session-auth.md) | 서버 세션 단일 인증 (매직링크 + 쿠키) | 채택 |
| [0006](0006-app-edge-stays-caddy.md) | 엣지 프록시 선택: Caddy vs nginx | 채택 |
| [0007](0007-single-database-for-prod-and-dev.md) | 운영과 개발이 같은 데이터베이스를 사용 | 채택 |

## 관련 문서

리뷰 라운드별 세부 수정 이력은
[기능 분해 명세 §6.2](../specs/functional-specification.md)의 정합성
표를 참조한다. ADR은 그중 구조적·재논의 결정만 승격해 기록한다.
