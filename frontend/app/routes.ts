import {
  type RouteConfig,
  index,
  layout,
  prefix,
  route,
} from "@react-router/dev/routes";

export default [
  index("routes/home.tsx"),

  // 공개 — 세션 없이 접근한다. 계정 없이 열 수 있는 화면은 이 둘과 랜딩뿐이다.
  route("login", "routes/login/login.tsx"),
  route("register", "routes/register/register.tsx"),

  layout(
    "routes/onboarding/layout.tsx",
    prefix("onboarding", [
      route("profile", "routes/onboarding/profile.tsx"),
      route("income", "routes/onboarding/income.tsx"),
      route("expense", "routes/onboarding/expense.tsx"),
      route("skills", "routes/onboarding/skills.tsx"),
      route("review", "routes/onboarding/review.tsx"),
    ]),
  ),

  layout(
    "routes/dashboard/layout.tsx",
    prefix("dashboard", [
      index("routes/dashboard/index.tsx"),
      route("history", "routes/dashboard/history.tsx"),
      route("trends", "routes/dashboard/trends.tsx"),
      route("simulator", "routes/dashboard/simulator.tsx"),
    ]),
  ),

  layout(
    "routes/mypage/layout.tsx",
    prefix("mypage", [
      route("records", "routes/mypage/records.tsx"),
      route("profile", "routes/mypage/profile.tsx"),
    ]),
  ),

  // 관리자 — Collector 파이프라인 관리 콘솔
  route("admin/collector", "routes/admin/collector.tsx"),
  route("admin/pipeline", "routes/admin/collector.tsx"),
] satisfies RouteConfig;
