import js from "@eslint/js";
import reactHooks from "eslint-plugin-react-hooks";
import react from "eslint-plugin-react";
import tseslint from "typescript-eslint";
import prettier from "eslint-config-prettier";
import globals from "globals";

// UI 통일 P4 — 공용 계층과 raw 복제본이 영구 공존하지 않게 막는 유일한 장치다
// (docs/plans/ui-unification-plan.md §5). 색은 carix 토큰, 버튼은 Button 프리미티브로만.
const PALETTE =
  "\\b(bg|text|border|ring|fill|stroke|from|to|via|outline|divide|decoration|shadow|accent|caret|placeholder)-(slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-(50|[1-9]00|950)\\b";
const PALETTE_MESSAGE =
  "토큰 밖 Tailwind 팔레트 색이다. carix 토큰(bg-carix-*, text-carix-* 등)을 쓴다.";
const UI_RULES = [
  "error",
  {
    selector: "JSXOpeningElement[name.name='button']",
    message: "raw <button> 대신 @/components/ui/button 의 Button 을 쓴다.",
  },
  { selector: `Literal[value=/${PALETTE}/]`, message: PALETTE_MESSAGE },
  {
    selector: `TemplateElement[value.raw=/${PALETTE}/]`,
    message: PALETTE_MESSAGE,
  },
  {
    // 화면 이동 글자 링크를 손으로 다시 쓰는 실수를 잡는다. 변수·템플릿 문자열 우회까지는 막지 않는다.
    selector:
      "JSXOpeningElement[name.name='Link'] JSXAttribute[name.name='className'] Literal[value=/(?=.*text-carix-info)(?=.*(?:^|[\\s:])underline(?:\\s|$))/]",
    message:
      "화면 이동 글자 링크는 @/components/ui/text-link 의 TextLink 를 쓴다.",
  },
];

// 화면 틀 — 제목이 헤더 로고 선에서 시작하고 제목 줄 구성이 같도록 화면 파일은 공용 틀만 쓴다.
const PAGE_RULES = [
  {
    selector: "JSXOpeningElement[name.name='h1']",
    message: "화면 제목은 @/components/layout/PageHeader 로 그린다.",
  },
  {
    selector:
      "Literal[value=/\\bmx-auto\\b.*\\bmax-w-(3xl|4xl|5xl|7xl|screen-2xl)\\b/]",
    message: "화면 틀은 @/components/layout/PageFrame 으로 그린다.",
  },
  {
    selector: "JSXOpeningElement[name.name='h2']",
    message:
      '섹션 제목은 @/components/layout/PageSection 또는 CardTitle as="h2" 로 그린다.',
  },
  {
    selector: "JSXOpeningElement[name.name=/^h[4-6]$/]",
    message: "화면 안 제목은 두 단계(h2 섹션·h3 소제목)다.",
  },
];

export default tseslint.config(
  { ignores: ["build", ".react-router", "node_modules"] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ["**/*.{ts,tsx}"],
    languageOptions: {
      globals: {
        ...globals.browser,
      },
    },
    plugins: {
      react,
      "react-hooks": reactHooks,
    },
    rules: {
      ...react.configs.recommended.rules,
      ...reactHooks.configs.recommended.rules,
      "react/react-in-jsx-scope": "off",
    },
    settings: {
      react: {
        version: "detect",
      },
    },
  },
  {
    files: ["app/**/*.tsx"],
    ignores: ["app/components/ui/**", "app/**/*.test.tsx"],
    rules: { "no-restricted-syntax": UI_RULES },
  },
  {
    // 같은 규칙 이름을 뒤 블록이 덮으므로 UI_RULES 를 함께 펼친다.
    files: ["app/routes/**/*.tsx"],
    ignores: [
      "app/**/*.test.tsx",
      "app/routes/home.tsx",
      "app/routes/login/**",
      "app/routes/register/**",
      "app/routes/dev/**",
    ],
    rules: { "no-restricted-syntax": [...UI_RULES, ...PAGE_RULES] },
  },
  {
    // §5 교체표의 남은 일이다. 소유자가 raw 버튼·팔레트를 걷어 내면 여기서 한 줄씩 지운다.
    // 새 파일은 이 목록에 넣지 않는다 — 넣는 순간 공존이 굳는다.
    files: [
      "app/components/admin/AdminKeyModal.tsx", // 박지수
      "app/components/admin/CollectorStatsCards.tsx", // 박지수
      "app/components/admin/CollectorStatusBanner.tsx", // 박지수
      "app/components/admin/JobHistoryTable.tsx", // 박지수
      "app/components/admin/PipelineControlPanel.tsx", // 박지수
      "app/routes/admin/collector.tsx", // 박지수
      "app/components/dashboard/SkillDemandSection.tsx", // B
      "app/routes/dashboard/history.tsx", // B
      "app/routes/dashboard/trends.tsx", // B
      "app/routes/onboarding/skills.tsx", // B
    ],
    rules: { "no-restricted-syntax": "off" },
  },
  {
    files: [
      "server.js",
      "server.test.js",
      "vite.config.ts",
      "react-router.config.ts",
      "playwright.config.ts",
    ],
    languageOptions: {
      globals: {
        ...globals.node,
      },
    },
  },
  prettier,
);
