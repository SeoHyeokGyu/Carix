import { useState } from "react";
import { FORMULAS } from "../../content/landing";

export function FormulaSummary() {
  const [showFormula, setShowFormula] = useState<Record<string, boolean>>({});

  const toggleFormula = (title: string) => {
    setShowFormula((prev) => ({
      ...prev,
      [title]: !prev[title],
    }));
  };

  return (
    <section aria-labelledby="formula-heading" className="px-6 py-16">
      <div className="mx-auto max-w-5xl">
        <div className="flex items-center justify-center gap-2">
          <span className="text-2xl" aria-hidden="true">
            🧮
          </span>
          <h2
            id="formula-heading"
            className="font-display text-2xl font-bold text-carix-text"
          >
            계산 방식
          </h2>
        </div>
        <p className="mt-2 text-sm text-carix-text/75 text-center">
          지수를 산출하는 합리적인 기준과 근거입니다.
        </p>

        {/* 모바일은 세로 피드, 데스크톱은 가로 그리드 배치 */}
        <div className="mt-10 grid gap-6 md:grid-cols-2">
          {FORMULAS.map((formula) => {
            const isOpen = showFormula[formula.title] || false;
            return (
              <div
                key={formula.title}
                className="rounded-2xl border border-carix-border bg-carix-surface p-6 shadow-sm"
              >
                <div className="flex items-center justify-between">
                  <h3 className="font-display text-lg font-bold text-carix-text">
                    {formula.title}
                  </h3>
                  <div
                    onClick={() => toggleFormula(formula.title)}
                    className="flex h-8 w-8 items-center justify-center rounded-full bg-carix-info/10 hover:bg-carix-info/20 text-carix-info font-bold transition-all cursor-pointer text-sm select-none"
                    title="상세 공식 보기"
                    tabIndex={0}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        toggleFormula(formula.title);
                      }
                    }}
                  >
                    ❓
                  </div>
                </div>

                <p className="mt-4 text-sm leading-relaxed text-carix-text/80">
                  {formula.note}
                </p>

                <div
                  className={
                    isOpen
                      ? "mt-4 overflow-hidden rounded-xl bg-carix-bg p-4 border border-carix-border animate-fadeIn block"
                      : "hidden"
                  }
                >
                  <pre className="overflow-x-auto">
                    <code className="whitespace-pre text-xs text-carix-info font-mono">
                      {formula.formula}
                    </code>
                  </pre>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
