import { ThemeToggle } from "../components/ThemeToggle";
import { Hero } from "../components/hero/Hero";
import { CoreIndexCards } from "../components/sections/CoreIndexCards";
import { DataFlowDiagram } from "../components/sections/DataFlowDiagram";
import { FormulaSummary } from "../components/sections/FormulaSummary";
import { FeatureList } from "../components/sections/FeatureList";
import { ResultExample } from "../components/sections/ResultExample";
import { BetaNotice } from "../components/sections/BetaNotice";

export default function Home() {
  return (
    <main className="relative min-h-screen bg-carix-bg text-carix-text transition-colors duration-300">
      <ThemeToggle />
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
