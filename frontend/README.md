# Carix Frontend

목표 구성: Vite + React + TypeScript + React Router Framework Mode + Node SSR(Express).

상세 설계: [`../docs/specs/frontend-design.md`](../docs/specs/frontend-design.md)

## 현재 상태

Vite + React Router + Tailwind CSS 기반으로 `S-01`~`S-16` 전 화면이 실제 API 에 연결돼 동작합니다. ESLint, Prettier, 듀얼 테마(Light/Dark), WCAG AA 접근성이 적용돼 있습니다.

`package.json` 스크립트:

- `pnpm dev`: Vite 개발 서버
- `pnpm build`: React Router 빌드
- `pnpm typecheck`: TypeScript 정적 타입 검사
- `pnpm lint`: ESLint 검사
- `pnpm format:check`: Prettier 포맷 검사 (CI 가 검증)
- `pnpm test`: 서버 스모크 테스트 (pretest 빌드 포함)
- `pnpm test:unit`: Vitest 컴포넌트 단위 테스트
- `pnpm test:e2e`: Playwright E2E 및 접근성 테스트

> **PR 자동 검증.** PR 단계에서 `.github/workflows/pr-check.yml`이 `format:check`, `lint`, `typecheck`, `test:unit`, `build`를 자동 검증합니다. 배포 워크플로(`deploy.yml`)는 main 머지 시 Docker 이미지를 빌드합니다.

```bash
pnpm install --frozen-lockfile
pnpm format:check && pnpm lint && pnpm typecheck && pnpm test:unit && pnpm build
```

## 로컬 개발

- Docker 불필요. Vite dev server(`pnpm dev`)를 띄웁니다.
- `/api` 프록시는 `vite.config.ts` 가 갖습니다. `pnpm dev:local`(로컬 게이트웨이 8080)·`pnpm dev:collector`(8083)로 대상을 바꿉니다.
