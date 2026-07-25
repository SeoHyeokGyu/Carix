# 0002. 프런트엔드 Vite + React + Node SSR (Next.js 대체)

- 상태: 채택
- 날짜: 2026-07-18

## 맥락

초기 설계는 Next.js App Router를 프런트엔드로 지정했다. 공개 랜딩·콘텐츠의 SEO와
로그인 후 대시보드의 높은 상호작용성을 한 앱에서 지원하되, 백엔드(Spring Boot)와
역할이 겹치지 않는 구성이 필요했다.

## 결정

Vite + React + TypeScript + React Router Framework Mode + Node SSR(Express)로 간다.
상세: [Vite·Node 프런트엔드 설계](../specs/frontend-design.md).

- 런타임 Node.js 24 LTS, 빌드 Vite, 라우팅·렌더링 React Router Framework Mode.
- Node 서버는 HTML SSR·SEO만 담당하고 DB 직접 접근·비즈니스 로직 복제를 하지 않는다.
- Spring Boot가 인증·도메인·API·데이터 접근을 담당한다.

## 대안

- **Next.js App Router 유지** — 풀스택 프레임워크라 Node 계층이 API·데이터로 번지기
  쉽고, Spring Boot와 역할 경계가 흐려진다.

## 결과

- 렌더링 전략: `/` 사전 렌더링, `/login`·`/auth/*` SSR, `/dashboard`·`/settings` SSR
  shell + hydration.
- 전 설계 문서의 Next.js 표기를 React(Vite + Node SSR)로 갱신. 스킬 사전의 `Next.js`
  별칭은 탐지 대상 스킬로 별개 유지.
