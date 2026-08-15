import { useState } from "react";
import { DISCLAIMER } from "../../content/landing";

export function ResultExample() {
  const [showFormulaTooltip, setShowFormulaTooltip] = useState(false);

  return (
    <section aria-labelledby="result-example-heading" className="px-6 py-16">
      <div className="mx-auto max-w-2xl">
        <div className="flex items-center justify-center gap-2">
          <span className="text-2xl" aria-hidden="true">
            📊
          </span>
          <h2
            id="result-example-heading"
            className="font-display text-2xl font-bold text-carix-text"
          >
            결과 예시
          </h2>
        </div>
        <p className="mt-2 text-sm text-carix-text/75 text-center">
          실제 사용자에게 제공되는 직관적인 가치 리포트 예시입니다.
        </p>

        {/* 큰 카드형 레이아웃 */}
        <div className="mt-10 overflow-hidden rounded-3xl border border-carix-border bg-carix-surface p-6 md:p-8 shadow-sm">
          <div className="flex items-center justify-between border-b border-carix-border pb-4">
            <span className="text-xs font-semibold text-carix-info uppercase tracking-wider">
              커리어 금융 리포트 샘플
            </span>
            <span className="text-xs text-carix-text/75">방금 업데이트됨</span>
          </div>

          <div className="mt-8 flex flex-col gap-8">
            {/* 구매력 지수 */}
            <div>
              <div className="flex items-baseline justify-between">
                <div className="flex items-center gap-2">
                  <span className="text-xl" role="img" aria-label="돈">
                    💰
                  </span>
                  <h3 className="font-display text-base font-bold text-carix-text">
                    구매력 지수
                  </h3>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-2xl font-extrabold text-carix-warning">
                    94
                  </span>
                  <span className="rounded-full bg-carix-warning/5 px-2.5 py-0.5 text-xs font-bold text-carix-warning">
                    하락
                  </span>
                </div>
              </div>

              {/* 친근한 둥근 프로그레스 바 */}
              <div className="mt-3 h-3 w-full overflow-hidden rounded-full bg-carix-bg">
                <div
                  className="h-full rounded-full bg-carix-warning transition-all duration-500"
                  style={{ width: "94%" }}
                ></div>
              </div>

              <div className="mt-3 text-sm leading-relaxed text-carix-text/80">
                연봉은 3년간 6.0% 올랐지만 개인물가는 12.4% 상승해 실질 구매력은
                감소했습니다.{" "}
                <div
                  onClick={() => setShowFormulaTooltip(!showFormulaTooltip)}
                  className="inline-flex h-5 w-5 items-center justify-center rounded-full bg-carix-info/10 text-carix-info text-[10px] font-bold cursor-pointer hover:bg-carix-info/20 select-none"
                  title="상세 공식 설명 보기"
                  tabIndex={0}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      setShowFormulaTooltip(!showFormulaTooltip);
                    }
                  }}
                >
                  ?
                </div>
                {showFormulaTooltip && (
                  <div className="mt-2 rounded-xl bg-carix-bg p-3 border border-carix-border text-xs text-carix-info font-mono">
                    계산 공식: 명목소득 지수 106.0 ÷ 개인물가 지수 112.4 × 100 ≈
                    94
                  </div>
                )}
              </div>
            </div>

            {/* 시장가치 지수 */}
            <div>
              <div className="flex items-baseline justify-between">
                <div className="flex items-center gap-2">
                  <span className="text-xl" role="img" aria-label="상승 차트">
                    📈
                  </span>
                  <h3 className="font-display text-base font-bold text-carix-text">
                    시장가치 지수
                  </h3>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-2xl font-extrabold text-carix-positive">
                    117
                  </span>
                  <span className="rounded-full bg-carix-positive/10 px-2.5 py-0.5 text-xs font-bold text-carix-positive">
                    상승
                  </span>
                </div>
              </div>

              {/* 친근한 둥근 프로그레스 바 */}
              <div className="mt-3 h-3 w-full overflow-hidden rounded-full bg-carix-bg">
                <div
                  className="h-full rounded-full bg-carix-positive transition-all duration-500"
                  style={{ width: "100%" }} // 100을 넘어가는 수치는 100% 채움
                ></div>
              </div>

              <p className="mt-3 text-sm leading-relaxed text-carix-text/80">
                보유 스킬의 채용 수요는 같은 기간 증가했고, 현재 연봉은 동일
                조건의 공식 임금통계 중앙값보다 낮습니다.
              </p>
            </div>

            {/* 결정 신호 */}
            <div className="rounded-2xl bg-carix-bg p-5 border border-carix-border">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="text-xl" role="img" aria-label="저울">
                    ⚖️
                  </span>
                  <h4 className="font-display text-sm font-bold text-carix-text">
                    추천 결정 신호
                  </h4>
                </div>
                <span className="rounded-full bg-carix-info/10 px-3.5 py-1 text-sm font-bold text-carix-info">
                  이직 탐색
                </span>
              </div>
              <p className="mt-3 text-sm leading-relaxed text-carix-text/80">
                소비 절감 노력보다는 연봉협상이나 이직 준비를 시도하는 것이
                경제적으로 기대효과가 훨씬 더 큰 상태입니다.
              </p>
            </div>
          </div>
        </div>

        <p className="mt-4 text-xs leading-relaxed text-carix-text/80 text-center">
          {DISCLAIMER}
        </p>
      </div>
    </section>
  );
}
