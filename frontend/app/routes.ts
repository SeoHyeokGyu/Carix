import {
  type RouteConfig,
  index,
  layout,
  prefix,
  route,
} from "@react-router/dev/routes";

export default [
  index("routes/home.tsx"),

  // 공개 — 세션 없이 접근한다. /auth/verify 는 링크 토큰으로 들어온다.
  route("login", "routes/login/login.tsx"),
  route("login/sent", "routes/login/sent.tsx"),
  route("auth/verify", "routes/auth/verify.tsx"),

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
] satisfies RouteConfig;
