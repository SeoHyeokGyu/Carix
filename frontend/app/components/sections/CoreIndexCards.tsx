import { CORE_INDEX_CARDS } from "../../content/landing";
import { Card } from "@/components/ui/card";

export function CoreIndexCards() {
  const getEmoji = (title: string) => {
    if (title.includes("구매력")) return "💰";
    if (title.includes("시장가치")) return "📈";
    return "⚖️";
  };

  return (
    <section aria-labelledby="core-index-heading" className="px-6 py-16">
      <div className="mx-auto max-w-5xl">
        <div className="flex items-center justify-center gap-2">
          <span className="text-2xl" aria-hidden="true">
            🧭
          </span>
          <h2
            id="core-index-heading"
            className="font-display text-2xl font-bold text-carix-text"
          >
            핵심 지수
          </h2>
        </div>
        <p className="mt-2 text-sm text-carix-text/75 text-center">
          커리어의 경제적 가치를 평가하는 3가지 기본 지표입니다.
        </p>

        {/* 모바일은 세로 피드, 데스크톱은 가로 그리드 배치 */}
        <div className="mt-10 grid gap-6 md:grid-cols-3">
          {CORE_INDEX_CARDS.map((card) => (
            <article key={card.title}>
              <Card className="h-full gap-0 transition-transform duration-200 hover:-translate-y-0.5">
                <div className="flex items-center gap-3">
                  <span className="text-3xl" role="img" aria-label={card.title}>
                    {getEmoji(card.title)}
                  </span>
                  <h3 className="font-display text-xl font-bold text-carix-text">
                    {card.title}
                  </h3>
                </div>
                <p className="mt-4 text-base leading-relaxed text-carix-text/80">
                  {card.description}
                </p>
                <div className="mt-5 pt-4 border-t border-carix-border flex items-center justify-between text-xs">
                  <span className="text-carix-text/80 font-medium">
                    분석 기준
                  </span>
                  <span className="text-carix-info font-semibold">
                    {card.inputs}
                  </span>
                </div>
              </Card>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
