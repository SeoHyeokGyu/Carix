import { HelpTooltip } from "../common/HelpTooltip";

/** `S-10` 지수·신호 도움말. 문구는 개편 전 카드에 있던 그대로다. */
export function PurchasingPowerHelp() {
  return (
    <HelpTooltip label="구매력 지수">
      <div className="flex flex-col gap-1.5">
        <p className="font-semibold text-carix-text">구매력 지수 (PPI)</p>
        <p className="text-carix-text/80">
          내 소득 상승률과 소비자물가지수(CPI)를 비교하여 실질 소득의 구매력
          변화를 측정합니다.
        </p>
        <ul className="text-carix-text/70 space-y-1 mt-1">
          <li className="flex items-start gap-1.5">
            <span className="shrink-0 px-1.5 py-0.5 rounded bg-carix-positive/10 text-carix-positive font-semibold text-[10px]">
              100 초과
            </span>
            <span>물가보다 소득이 더 올라 실질 구매력 증가</span>
          </li>
          <li className="flex items-start gap-1.5">
            <span className="shrink-0 px-1.5 py-0.5 rounded bg-carix-warning/10 text-carix-warning font-semibold text-[10px]">
              100 미만
            </span>
            <span>물가 상승률에 못 미쳐 실질 소득 감소</span>
          </li>
        </ul>
      </div>
    </HelpTooltip>
  );
}

export function MarketValueHelp() {
  return (
    <HelpTooltip label="시장가치 지수">
      <div className="flex flex-col gap-1.5">
        <p className="font-semibold text-carix-text">시장가치 지수 (MVI)</p>
        <p className="text-carix-text/80">
          보유 스킬과 경력 조건의 시장 가치를 고용노동부 공식 임금 통계와
          비교합니다.
        </p>
        <ul className="text-carix-text/70 space-y-1 mt-1">
          <li className="flex items-start gap-1.5">
            <span className="shrink-0 px-1.5 py-0.5 rounded bg-carix-positive/10 text-carix-positive font-semibold text-[10px]">
              100 초과
            </span>
            <span>보유 스킬 수요가 높아 시장 평균 대비 높은 가치 형성</span>
          </li>
          <li className="flex items-start gap-1.5">
            <span className="shrink-0 px-1.5 py-0.5 rounded bg-carix-info/10 text-carix-info font-semibold text-[10px]">
              100 기준
            </span>
            <span>동료집단(동일 연차·직무) 공식 임금 중앙값 수준</span>
          </li>
        </ul>
      </div>
    </HelpTooltip>
  );
}

export function DecisionSignalHelp() {
  return (
    <HelpTooltip label="결정 신호">
      <div className="flex flex-col gap-1.5">
        <p className="font-semibold text-carix-text">
          결정 신호 (Decision Signal)
        </p>
        <p className="text-carix-text/80">
          구매력, 시장가치, 채용 활발도와 연봉 격차를 종합 분석하여 도출된 행동
          가이드입니다.
        </p>
        <p className="text-carix-text/70 mt-0.5">
          이직 탐색, 연봉 협상, 스킬 강화, 현재 유지 등의 신호를 제공합니다.
        </p>
      </div>
    </HelpTooltip>
  );
}

export function OpeningWindowHelp() {
  return (
    <HelpTooltip label="채용 활발도" align="right">
      <div className="flex flex-col gap-1.5">
        <p className="font-semibold text-carix-text">채용 활발도 (OWI)</p>
        <p className="text-carix-text/80">
          전체 신규 수집 공고 중 내 동료집단 공고가 차지하는 최근 3개월 비중을
          이전 12개월 비중과 비교한 지표입니다.
        </p>
        <ul className="text-carix-text/70 space-y-1 mt-1">
          <li className="flex items-start gap-1.5">
            <span className="shrink-0 px-1.5 py-0.5 rounded bg-carix-info/10 text-carix-info font-semibold text-[10px]">
              100 초과
            </span>
            <span>동료집단 채용 공고 비중이 이전 12개월 대비 확대</span>
          </li>
          <li className="flex items-start gap-1.5">
            <span className="shrink-0 px-1.5 py-0.5 rounded bg-carix-warning/10 text-carix-warning font-semibold text-[10px]">
              100 미만
            </span>
            <span>동료집단 채용 공고 비중이 이전 12개월 대비 축소</span>
          </li>
        </ul>
      </div>
    </HelpTooltip>
  );
}
