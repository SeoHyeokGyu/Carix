import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

import type {
  EvaluateRequest,
  LegacyScenario,
  SavedScenario,
} from "../../lib/api/scenario";
import { formatKoreanSalary } from "../../lib/koreanSalary";
import { signedMan } from "../../lib/simulatorCopy";

interface SavedScenarioListProps {
  scenarios: readonly SavedScenario[];
  /** 엔진 2 항목: 저장한 요청으로 다시 평가해 결과 화면을 연다. */
  onLoad: (request: EvaluateRequest) => void;
  /** 엔진 1 항목: 그 입력을 새 방식 요청으로 옮겨 ① 부터 다시 시작한다. */
  onRecalculate: (request: EvaluateRequest) => void;
}

const DATE_LENGTH = 10;

function title(item: SavedScenario): string {
  const name = item.name?.trim();
  const date = item.calculatedAt.slice(0, DATE_LENGTH);
  return name ? `${name} · ${date}` : date;
}

/** 엔진 1 입력을 엔진 2 요청으로 옮긴다. 상여는 연봉에 포함됐다고 본다(이전 방식에는 구분이 없었다). */
export function legacyToRequest(legacy: LegacyScenario): EvaluateRequest {
  const { inputs } = legacy;
  return {
    stay: {
      raisePercent: inputs.stayExpectedRaiseRate,
      currentGrossOverride: inputs.currentGrossSalary,
    },
    move: {
      grossSalary: inputs.moveTargetGrossSalary,
      unemploymentMonths: inputs.expectedUnemploymentMonths,
      oneTimeCost: inputs.moveOneTimeCost,
      benefitEligible: inputs.isEligibleForUnemploymentBenefit,
      bonusIncluded: true,
    },
  };
}

function LegacyBody({
  legacy,
  onRecalculate,
}: {
  legacy: LegacyScenario;
  onRecalculate: () => void;
}) {
  const { result } = legacy;
  return (
    <div className="mt-2 flex flex-col gap-2 text-sm">
      <div className="flex flex-wrap items-center gap-2">
        <Badge>이전 방식(세전 기준)</Badge>
        <Badge>{result.typeLabel}</Badge>
        <span>차이 {signedMan(result.difference)}</span>
      </div>
      <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-muted-foreground">
        <dt>잔류 12개월 보상</dt>
        <dd>{result.stayCompensation12m.toLocaleString("ko-KR")}원</dd>
        <dt>이직 12개월 보상</dt>
        <dd>{result.moveCompensation12m.toLocaleString("ko-KR")}원</dd>
      </dl>
      <div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onRecalculate}
        >
          새 방식으로 다시 계산
        </Button>
      </div>
    </div>
  );
}

/** 저장된 시나리오. 최근 순이며 비어 있으면 그리지 않는다. */
export function SavedScenarioList({
  scenarios,
  onLoad,
  onRecalculate,
}: SavedScenarioListProps) {
  if (scenarios.length === 0) return null;
  return (
    <section aria-labelledby="saved-scenarios" className="flex flex-col gap-3">
      <h3 id="saved-scenarios" className="text-base font-semibold">
        저장된 시나리오
      </h3>
      <ul className="flex flex-col gap-3">
        {scenarios.map((item) => {
          const { legacy, request } = item;
          return (
            <li key={item.id} className="rounded-lg border px-4 py-3">
              <p className="font-medium">{title(item)}</p>
              {item.engineVersion === 1 && legacy && (
                <LegacyBody
                  legacy={legacy}
                  onRecalculate={() => onRecalculate(legacyToRequest(legacy))}
                />
              )}
              {item.engineVersion === 2 && request && (
                <div className="mt-2 flex flex-wrap items-center gap-3 text-sm">
                  {request.move && (
                    <span className="text-muted-foreground">
                      이직 연봉 {formatKoreanSalary(request.move.grossSalary)}
                    </span>
                  )}
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => onLoad(request)}
                  >
                    불러오기
                  </Button>
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
