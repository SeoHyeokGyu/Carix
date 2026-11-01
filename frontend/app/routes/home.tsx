import { Link } from "react-router";

import { Hero } from "../components/hero/Hero";
import { BetaNotice } from "../components/sections/BetaNotice";
import { CoreIndexCards } from "../components/sections/CoreIndexCards";
import { DataFlowDiagram } from "../components/sections/DataFlowDiagram";
import { FeatureList } from "../components/sections/FeatureList";
import { FormulaSummary } from "../components/sections/FormulaSummary";
import { ResultExample } from "../components/sections/ResultExample";
import { ThemeToggle } from "../components/ThemeToggle";

export default function Home() {
  return (
    <main className="relative min-h-screen bg-carix-bg text-carix-text transition-colors duration-300">
      <ThemeToggle />
      {/* 하단까지 내려가지 않고도 기존 사용자가 바로 로그인하도록 테마 버튼 옆에 둔다. */}
      <Link
        to="/login"
        className="fixed top-6 right-20 z-50 flex h-11 items-center rounded-full border border-carix-border bg-carix-surface/90 px-4 text-sm font-medium text-carix-text shadow-md backdrop-blur-md transition-colors hover:bg-carix-border/40"
      >
        로그인
      </Link>
      <Hero />
      <CoreIndexCards />
      <DataFlowDiagram />
      <FormulaSummary />
      <FeatureList />
      <ResultExample />
      <BetaNotice />
    </main>
  );
}
