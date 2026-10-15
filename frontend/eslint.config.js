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
const CARD_FRAME =
  "\\brounded-xl border border-carix-border bg-carix-surface(?=\\s|$)";
const CARD_MESSAGE = "카드 틀은 @/components/ui/card 의 Card 를 쓴다.";
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
  {
    selector:
      "MemberExpression[object.name=/^(window|globalThis)$/][property.name=/^(confirm|alert|prompt)$/]",
    message: "확인은 @/components/ui/confirm-dialog 의 ConfirmDialog 로 한다.",
  },
  {
    selector: "CallExpression[callee.name=/^(confirm|alert|prompt)$/]",
    message: "확인은 @/components/ui/confirm-dialog 의 ConfirmDialog 로 한다.",
  },
  {
    // 카드 틀을 클래스 문자열로 복제하는 실수를 잡는다. 반투명 표면(`bg-carix-surface/95`)인 툴팁·드롭다운은 카드가 아니다.
    selector: `Literal[value=/${CARD_FRAME}/]`,
    message: CARD_MESSAGE,
  },
  {
    selector: `TemplateElement[value.raw=/${CARD_FRAME}/]`,
    message: CARD_MESSAGE,
  },
  {
    selector: "Literal[value=/\\b(text|bg|border|ring)-destructive\\b/]",
    message: "위험 색은 carix-danger 다(text-carix-danger 등).",
  },
  {
    selector:
      "TemplateElement[value.raw=/\\b(text|bg|border|ring)-destructive\\b/]",
    message: "위험 색은 carix-danger 다(text-carix-danger 등).",
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
