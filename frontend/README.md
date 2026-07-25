# Carix Frontend

Vite + React + TypeScript + React Router Framework Mode + Node SSR(Express).

상세 설계: [`../docs/specs/frontend-design.md`](../docs/specs/frontend-design.md)

아직 스캐폴드 전. 다음 단계에서 Vite 앱과 Node SSR 서버를 초기화한다.

## 로컬 개발

- Docker 불필요. Vite dev server를 띄우고 `/api`를 백엔드로 프록시한다.
- 백엔드는 OCI dev DB에 원격 연결하며, 로컬엔 DB를 띄우지 않는다 (루트 README 개발 환경 참조).
