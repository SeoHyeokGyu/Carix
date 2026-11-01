import { Button } from "@/components/ui/button";

import { HERO_INTRO } from "../../content/landing";
import { IndexCrossoverLine } from "./IndexCrossoverLine";

export function Hero() {
  const getQuestionStyle = (index: number) => {
    // 각각의 질문카드에 서로 다른 강조 테두리색과 이모지 지정
    const styles = [
      { emoji: "💰", border: "border-l-4 border-l-carix-info" },
      { emoji: "📈", border: "border-l-4 border-l-carix-positive" },
      { emoji: "⚖️", border: "border-l-4 border-l-carix-warning" },
    ];
    return styles[index % styles.length];
  };

  return (
    <section
      aria-labelledby="hero-heading"
      className="px-6 py-20 md:py-28 relative overflow-hidden"
    >
      <div className="mx-auto flex max-w-7xl flex-col items-center gap-12 text-center md:flex-row md:text-left">
        <div className="flex-1">
          {/* 눈길을 끄는 최상단 필 배지 */}
          <div className="inline-flex items-center gap-1.5 rounded-full border border-carix-info/20 bg-carix-info/5 px-3 py-1.5 text-xs font-semibold text-carix-info mb-6 animate-pulse select-none">
            <span>✨</span> 내 커리어의 경제적 실질가치 추적
          </div>

          {/* 그라데이션 타이틀 */}
          <h1
            id="hero-heading"
            className="font-display text-5xl font-extrabold tracking-tight md:text-6xl"
          >
            <span className="bg-gradient-to-r from-carix-info to-carix-positive bg-clip-text text-transparent">
              Carix
            </span>
          </h1>

          {HERO_INTRO.paragraphs.map((paragraph) => (
            <p
              key={paragraph}
              className="mt-6 text-lg leading-relaxed text-carix-text/80 font-medium"
            >
              {paragraph}
            </p>
          ))}

          {/* 시작 버튼은 하단 "시작하기" 한 곳에만 둔다 — 첫 화면에서는 그쪽으로 안내만 한다. */}
          <Button asChild size="lg" className="mt-8">
            <a href="#beta-notice-heading">시작하기 ↓</a>
          </Button>

          {/* 질문 카드 리스트 */}
          <ul className="mt-8 flex flex-col gap-3.5 text-left">
            {HERO_INTRO.questions.map((question, index) => {
              const style = getQuestionStyle(index);
              return (
                <li
                  key={question}
                  className={`rounded-xl border border-carix-border bg-carix-surface/90 px-5 py-4 text-carix-text flex items-start gap-3 shadow-sm hover:shadow transition-all duration-300 hover:-translate-y-0.5 ${style.border}`}
                >
                  <span
                    className="text-xl select-none"
                    role="img"
                    aria-hidden="true"
                  >
                    {style.emoji}
                  </span>
                  <span className="font-medium text-sm leading-snug">
                    {question}
                  </span>
                </li>
              );
            })}
          </ul>
        </div>

        {/* 우측 시그니처 그래픽 부분 (조명 후광 효과 추가) */}
        <div className="flex flex-1 justify-center relative w-full max-w-md md:max-w-none">
          <div className="absolute -inset-10 bg-carix-info/10 blur-3xl rounded-full opacity-60 pointer-events-none"></div>
          <div className="relative z-10 w-full flex justify-center">
            <IndexCrossoverLine />
          </div>
        </div>
      </div>
    </section>
  );
}
