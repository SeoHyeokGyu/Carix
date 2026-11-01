import { FEATURES } from "../../content/landing";

export function FeatureList() {
  const getFeatureEmoji = (index: number) => {
    const emojis = ["💰", "🧭", "📈", "⚖️", "💸", "🔄"];
    return emojis[index % emojis.length];
  };

  return (
    <section aria-labelledby="feature-heading" className="px-6 py-16">
      <div className="mx-auto max-w-7xl">
        <div className="flex items-center justify-center gap-2">
          <span className="text-2xl" aria-hidden="true">
            ✨
          </span>
          <h2
            id="feature-heading"
            className="font-display text-2xl font-bold text-carix-text"
          >
            주요 기능
          </h2>
        </div>
        <p className="mt-2 text-sm text-carix-text/75 text-center">
          Carix가 제공하는 개인 맞춤형 리포트 주요 기능들입니다.
        </p>

        {/* 모바일은 세로 피드, 데스크톱은 가로 그리드 배치 */}
        <ul className="mt-10 grid gap-4 md:grid-cols-2">
          {FEATURES.map((feature, index) => (
            <li
              key={feature}
              className="flex items-start gap-4 rounded-2xl border border-carix-border bg-carix-surface p-5 shadow-sm transition-all duration-300 hover:shadow-md"
            >
              <span className="text-2xl" role="img" aria-hidden="true">
                {getFeatureEmoji(index)}
              </span>
              <p className="text-sm font-medium leading-relaxed text-carix-text">
                {feature}
              </p>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
