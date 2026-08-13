import { FEATURES } from "../../content/landing";

export function FeatureList() {
  return (
    <section aria-labelledby="feature-heading" className="px-6 py-16">
      <div className="mx-auto max-w-5xl">
        <h2
          id="feature-heading"
          className="font-display text-2xl font-semibold text-carix-text"
        >
          주요 기능
        </h2>
        <ul className="mt-8 grid gap-3 md:grid-cols-2">
          {FEATURES.map((feature) => (
            <li
              key={feature}
              className="rounded-lg border border-carix-border bg-carix-surface/80 px-4 py-3 text-sm text-carix-text/90"
            >
              {feature}
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
