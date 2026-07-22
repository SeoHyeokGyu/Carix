# 0003. Docker·PostgreSQL은 OCI 서버 전용

- 상태: 채택
- 날짜: 2026-07-22

## 맥락

로컬 개발 머신에 Docker가 없고, 운영은 OCI VM의 Docker Compose로 한다. 로컬에
Postgres·Docker를 둘지, 아니면 서버 전용으로 할지 결정이 필요했다. 설계 테스트
전략은 Testcontainers를 쓰는데 이는 로컬 Docker를 요구한다.

## 결정

Docker와 PostgreSQL은 **OCI 서버에서만 기동**한다. 로컬은 순수 코드 개발.

- `docker-compose.yml`은 OCI 배포 산출물 전용이다. 로컬 `docker compose up` 아님.
- 로컬 백엔드는 OCI dev DB에 원격 연결한다 (SSH 터널 또는 방화벽 허용). 운영 DB와
  dev DB는 분리한다.
- 로컬 프런트엔드는 Vite dev server + `/api` 프록시로 Docker가 필요 없다.

## 대안

- **로컬 Docker/Postgres 허용** — Testcontainers 로컬 실행은 쉬워지나, 로컬 환경
  요구사항이 늘고 사용자 선택(서버 전용)과 어긋난다.
- **임베디드 DB(H2)** — 실 Postgres와 스키마·기능 드리프트 위험.

## 결과

- Testcontainers 통합 테스트는 로컬에서 못 돌린다 → CI(Docker 제공 러너) 또는 OCI
  dev DB 대상으로 실행한다. 로컬은 단위 테스트 중심.
  ([`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) backend 잡이 이를 실현.)
- 실행 절차는 [`docs/DEVELOPMENT.md`](../DEVELOPMENT.md)에 문서화.
