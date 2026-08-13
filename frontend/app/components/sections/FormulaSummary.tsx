import { FORMULAS } from "../../content/landing";

export function FormulaSummary() {
  return (
    <section aria-labelledby="formula-heading" className="px-6 py-16">
      <div className="mx-auto max-w-5xl">
        <h2
          id="formula-heading"
          className="font-display text-2xl font-semibold text-carix-text"
        >
          계산 방식
        </h2>
        <div className="mt-8 grid gap-6 md:grid-cols-2">
          {FORMULAS.map((formula) => (
            <div
              key={formula.title}
              className="min-w-0 rounded-xl border border-carix-border bg-carix-surface/80 p-6"
            >
              <h3 className="font-display text-lg font-semibold text-carix-text">
                {formula.title}
              </h3>
              <pre className="mt-4 overflow-x-auto rounded-lg bg-carix-bg p-4">
                <code className="whitespace-pre text-xs text-carix-positive">
                  {formula.formula}
                </code>
              </pre>
              <p className="mt-4 text-sm leading-relaxed text-carix-text/70">
                {formula.note}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
