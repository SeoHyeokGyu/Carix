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
