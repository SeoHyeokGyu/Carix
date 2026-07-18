# Vite and Node.js README Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Carix README에 Vite 기반 React 애플리케이션과 Node.js SSR 서버 구성을 현재 구현 스택으로 문서화한다.

**Architecture:** 기존 제품 설명은 유지하고 `데이터 원칙과 한계` 다음에 기술 구성 섹션을 추가한다. 새 섹션은 계층별 스택, 요청 흐름, 렌더링 전략, 테스트와 OCI 배포 단위를 요약하며 새 프런트엔드 설계 문서를 현재 기준으로 연결한다.

**Tech Stack:** Markdown, Git

## Global Constraints

- 프런트엔드 런타임은 Node.js 24 LTS로 표기한다.
- Vite, React, TypeScript, React Router Framework Mode, Express, `@react-router/express`를 현재 프런트엔드 구성으로 표기한다.
- Spring Boot가 인증, 도메인 로직, REST API, 데이터 접근을 소유한다.
- Node 서버는 DB에 직접 접근하거나 Spring Boot 비즈니스 로직을 복제하지 않는다.
- 공개 랜딩은 사전 렌더링하고 동적 콘텐츠는 Node SSR을 사용한다.
- 로그인 후 대시보드는 SSR shell 후 hydration을 사용한다.
- 기존 제품 설명, 지수 정의, MVP 범위와 데이터 원칙은 변경하지 않는다.
- README에서 Next.js를 현재 구현 스택으로 표현하지 않는다.

---

### Task 1: README에 Vite와 Node.js 프런트엔드 구성 반영

**Files:**
- Modify: `README.md:137-170`

**Interfaces:**
- Consumes: `docs/superpowers/specs/2026-07-18-vite-node-frontend-design.md`
- Produces: 저장소 방문자가 확인할 수 있는 현재 기술 구성과 배포 흐름

- [ ] **Step 1: 기존 Next.js 스택 문구 확인**

Run:

```bash
rg -n "구현 스택은 Next\.js" README.md
```

Expected: README 마지막 부분에서 기존 구현 스택 문구가 한 번 표시된다.

- [ ] **Step 2: 기술 구성 섹션 추가**

`데이터 원칙과 한계` 목록 다음, `프로젝트 상태` 앞에 아래 내용을 추가한다.

```md
## 기술 구성

| 계층 | 기술 | 책임 |
| --- | --- | --- |
| 프런트엔드 | Vite, React, TypeScript, React Router Framework Mode | UI, 라우팅, SSR·사전 렌더링 |
| Node SSR | Node.js 24 LTS, Express, `@react-router/express` | HTML 렌더링, SEO metadata, 공개 콘텐츠 |
| 클라이언트 데이터 | TanStack Query, React Hook Form, Zod | API 상태, 입력 폼, 검증 |
| UI·시각화 | Tailwind CSS, shadcn/ui, Recharts | 컴포넌트, 스타일, 지수 차트 |
| 백엔드 | Java 21, Spring Boot | 인증, 도메인 로직, REST API, 데이터 접근 |
| 데이터 | PostgreSQL, Spring Batch | 영속화, 외부 데이터 수집·집계 |
| 엣지·배포 | Nginx, Docker Compose, OCI | TLS, reverse proxy, 컨테이너 운영 |

```text
Internet
   |
Nginx
   |
   +-- /*       Node SSR :3000
   +-- /api/*   Spring Boot :8080
   +-- /assets  정적 파일
                    |
                PostgreSQL
```

Nginx는 동일 출처에서 화면과 API를 제공해 CORS 구성을 최소화합니다. Node 서버는
HTML과 SEO를 담당하고 Spring Boot는 인증과 비즈니스 로직을 담당합니다. Node
서버는 DB에 직접 접근하거나 Spring Boot 로직을 복제하지 않습니다.

### 렌더링 전략

- `/`, `/about`: 빌드 시 사전 렌더링
- `/content/:slug`: Node SSR
- `/login`: Node SSR
- `/dashboard/*`, `/settings/*`: SSR shell 후 hydration
- 차트·필터·사용자 데이터: hydration 이후 TanStack Query로 갱신

공개 페이지는 title, description, canonical URL, Open Graph metadata, sitemap,
robots 정책과 필요한 구조화 데이터를 제공합니다.

### 테스트

- 단위·컴포넌트: Vitest, Testing Library
- API 모킹: MSW
- 브라우저 E2E: Playwright

OCI VM에서는 `nginx`, `frontend-node`, `spring-api`, `spring-batch`,
`postgres` 컨테이너를 Docker Compose로 운영할 예정입니다.
```

- [ ] **Step 3: 상세 문서 목록에 프런트엔드 설계 링크 추가**

`상세 요구사항` 문서 목록의 마지막에 아래 항목을 추가한다.

```md
*   **Vite·Node.js 프런트엔드 설계:** [`docs/superpowers/specs/2026-07-18-vite-node-frontend-design.md`](docs/superpowers/specs/2026-07-18-vite-node-frontend-design.md)
```

- [ ] **Step 4: 기존 Next.js 구현 스택 문구 제거**

README 마지막의 아래 문장을 삭제한다.

```md
구현 스택은 Next.js, Java 21 Spring Boot, PostgreSQL, Spring Batch를 사용합니다.
```

- [ ] **Step 5: 내용과 범위 검증**

Run:

```bash
rg -n "기술 구성|Node.js 24 LTS|React Router Framework Mode|Spring Boot 로직을 복제하지|Vite·Node.js 프런트엔드 설계" README.md
rg -n "구현 스택은 Next\.js" README.md
git diff --check
git diff -- README.md
```

Expected:

- 첫 명령에서 새 섹션의 핵심 문구가 모두 표시된다.
- 두 번째 명령은 일치 항목 없이 종료 코드 1을 반환한다.
- `git diff --check` 출력이 없다.
- README diff에는 기술 구성 섹션, 설계 링크 추가와 기존 한 문장 삭제만 표시된다.

- [ ] **Step 6: README 변경 커밋**

```bash
git add README.md
git commit -m "docs: document Vite Node frontend stack"
```
