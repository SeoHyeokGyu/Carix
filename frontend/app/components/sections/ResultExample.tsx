import { RESULT_EXAMPLE, DISCLAIMER } from "../../content/landing";

export function ResultExample() {
  return (
    <section aria-labelledby="result-example-heading" className="px-6 py-16">
      <div className="mx-auto max-w-3xl">
        <h2
          id="result-example-heading"
          className="font-display text-2xl font-semibold text-carix-text"
        >
          결과 예시
        </h2>
        <pre className="mt-8 overflow-x-auto rounded-xl border border-carix-border bg-carix-surface/80 p-6">
          <code className="whitespace-pre-wrap font-display text-sm leading-relaxed text-carix-text">
            {RESULT_EXAMPLE}
          </code>
        </pre>
        <p className="mt-4 text-xs leading-relaxed text-carix-text/60">
          {DISCLAIMER}
        </p>
      </div>
    </section>
  );
}
