import { CORE_INDEX_CARDS } from "../../content/landing";

export function CoreIndexCards() {
  return (
    <section aria-labelledby="core-index-heading" className="px-6 py-16">
      <div className="mx-auto max-w-5xl">
        <h2
          id="core-index-heading"
          className="font-display text-2xl font-semibold text-carix-text"
        >
          핵심 지수
        </h2>
        <div className="mt-8 grid gap-6 md:grid-cols-3">
          {CORE_INDEX_CARDS.map((card) => (
            <article
              key={card.title}
              className="rounded-xl border border-carix-border bg-carix-surface/80 p-6"
            >
              <h3 className="font-display text-lg font-semibold text-carix-text">
                {card.title}
              </h3>
              <p className="mt-3 text-sm leading-relaxed text-carix-text/80">
                {card.description}
              </p>
              <p className="mt-4 text-xs text-carix-info">{card.inputs}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
