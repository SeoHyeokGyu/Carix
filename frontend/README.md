# Carix Frontend

목표 구성: Vite + React + TypeScript + React Router Framework Mode + Node SSR(Express).

상세 설계: [`../docs/specs/frontend-design.md`](../docs/specs/frontend-design.md)

## 현재 상태 — 배포 검증용 스텁

Vite·React는 아직 없다. 지금 있는 것은 `server.js` 하나로, `:3000`에서 평문 200을 돌려준다.
`carix-caddy`의 catch-all 라우팅이 `frontend-node:3000`을 향하므로 그 자리를 채우는 것이 목적이다.

`package.json` 스크립트 중 `typecheck`(`tsc --noEmit`)만 실제로 동작한다.
`lint`·`build`는 도구 선택 전이라 `TODO: (cjkang)` 표시한 no-op이며, CI가 요구하는 스크립트 이름을
미리 잡아둔 것이다. `dev`·`test:e2e`는 아직 없다.

실제 스캐폴드 시 교체 대상: `server.js`, 네 개 스크립트 전부, `Dockerfile`(단일 스테이지 →
멀티스테이지 pnpm install → build → 런타임 prod deps만).

> **자동 검증이 없다.** PR 단계 CI를 두지 않고, 배포 워크플로의 frontend 잡은 `docker build`만 한다.
> `lint`·`typecheck`·`test`는 커밋 전 로컬에서 직접 돌린다. 실제 스캐폴드 시 Dockerfile 빌드 단계에
> 넣을지 CI를 되살릴지 정한다.

```bash
pnpm install --frozen-lockfile
pnpm lint && pnpm typecheck && pnpm test && pnpm build
```

## 로컬 개발 (스캐폴드 이후)

- Docker 불필요. Vite dev server를 띄우고 `/api`를 백엔드로 프록시한다.
- 백엔드는 OCI dev DB에 원격 연결하며, 로컬엔 DB를 띄우지 않는다 (루트 README 개발 환경 참조).
