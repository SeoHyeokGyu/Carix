import { useRef, useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { TextLink } from "@/components/ui/text-link";

import type {
  EvaluateRequest,
  LegacyScenario,
  SavedScenario,
  ScenarioVerification,
} from "../../lib/api/scenario";
import { formatKoreanSalary } from "../../lib/koreanSalary";
import { signedMan } from "../../lib/simulatorCopy";

interface SavedScenarioMenuProps {
  scenarios: readonly SavedScenario[];
  /** 새 방식 항목: 저장한 요청으로 두 카드를 채워 다시 평가한다. title 은 줄 제목이다. */
  onLoad: (request: EvaluateRequest, title: string) => void;
  /** 이전 방식 항목: 그 입력을 새 방식 요청으로 옮겨 두 카드를 채운다. */
  onRecalculate: (request: EvaluateRequest, title: string) => void;
}

const DATE_LENGTH = 10;

function title(item: SavedScenario): string {
  const name = item.name?.trim();
  const date = item.calculatedAt.slice(0, DATE_LENGTH);
  return name ? `${name} · ${date}` : date;
}

/** 이전 방식 입력을 새 방식 요청으로 옮긴다. 상여는 연봉에 포함됐다고 본다(이전 방식에는 구분이 없었다). */
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

interface RowView {
  legacy: boolean;
  summary: string;
  actionLabel: string;
  request: EvaluateRequest;
}

function rowView(item: SavedScenario): RowView | null {
  const { legacy, request } = item;
  if (item.engineVersion === 1 && legacy) {
    return {
      legacy: true,
      summary: `세전 12개월 차이 ${signedMan(legacy.result.difference)}`,
      actionLabel: "다시 계산",
      request: legacyToRequest(legacy),
    };
  }
  if (item.engineVersion === 2 && request) {
    return {
      legacy: false,
      summary: request.move
        ? `새 회사 연봉 ${formatKoreanSalary(request.move.grossSalary)}`
        : "지금 회사만",
      actionLabel: "불러오기",
      request,
    };
  }
  return null;
}

function signedPercent(value: number | null) {
  if (value === null) return "-";
  const text = `${value.toFixed(1)}%`;
  return value > 0 ? `+${text}` : text;
}

/** 입력 가정과 실제를 사실로만 적는다. 선택이 옳았는지는 말하지 않는다. */
function verificationText(verification: ScenarioVerification) {
  switch (verification.status) {
    case "WAITING":
      return `검증 대기 (${verification.monthsLeft}개월 남음)`;
    case "STAY_COMPARED":
      return `예상 ${signedPercent(verification.expectedRaisePercent)}, 실제 ${signedPercent(verification.actualRaisePercent)}`;
    case "MOVE_COMPARED":
      return `오퍼 ${formatKoreanSalary(verification.offerAnnualGross)}, 실제 ${formatKoreanSalary(verification.actualAnnualGross)}`;
    case "NO_INCOME_AFTER":
      return "저장 후 소득 기록이 없어 비교할 수 없습니다";
    case "UNKNOWN_REASON":
      return "연봉 변경 사유가 없어 비교할 수 없습니다";
    case "NO_BASELINE":
      return "저장 당시 소득이 없어 비교할 수 없습니다";
    case "NO_OFFER":
      return "이직했지만 비교할 오퍼가 없습니다";
  }
}

function VerificationLine({
  verification,
}: {
  verification: ScenarioVerification;
}) {
  const needsIncome =
    verification.status === "NO_INCOME_AFTER" ||
    verification.status === "UNKNOWN_REASON";
  return (
    <p className="text-xs text-muted-foreground">
      <span>{verificationText(verification)}</span>
      {needsIncome && (
        <>
          {" "}
          <TextLink to="/mypage/records">소득 입력</TextLink>
        </>
      )}
    </p>
  );
}

/** 제목 옆에 붙는 저장 목록. 최근 순이며 비어 있으면 그리지 않는다. 고르면 닫힌다. */
export function SavedScenarioMenu({
  scenarios,
  onLoad,
  onRecalculate,
}: SavedScenarioMenuProps) {
  const [open, setOpen] = useState(false);
  // 줄을 골라 닫을 때는 포커스를 트리거로 돌리지 않는다 — 라우트가 페이지 제목으로 옮긴다.
  const chosen = useRef(false);
  if (scenarios.length === 0) return null;

  const choose = (row: RowView, rowTitle: string) => {
    chosen.current = true;
    setOpen(false);
    (row.legacy ? onRecalculate : onLoad)(row.request, rowTitle);
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button type="button" variant="outline" size="sm">
          저장된 시나리오 {scenarios.length} <span aria-hidden="true">▾</span>
        </Button>
      </PopoverTrigger>
      <PopoverContent
        align="end"
        collisionPadding={16}
        onCloseAutoFocus={(event) => {
          if (chosen.current) event.preventDefault();
          chosen.current = false;
        }}
        className="max-h-80 w-[calc(100vw-2rem)] overflow-y-auto p-2 sm:w-96"
      >
        <ul className="flex flex-col gap-1">
          {scenarios.map((item) => {
            const row = rowView(item);
            if (!row) return null;
            const rowTitle = title(item);
            return (
              <li
                key={item.id}
                className="flex items-center justify-between gap-3 rounded-md px-2 py-2"
              >
                <div className="flex min-w-0 flex-col gap-1">
                  <p className="truncate text-sm font-medium">{rowTitle}</p>
                  <p className="flex items-center gap-2 text-xs text-muted-foreground">
                    {row.legacy && <Badge variant="secondary">예전 계산</Badge>}
                    <span>{row.summary}</span>
                  </p>
                  {item.verification && (
                    <VerificationLine verification={item.verification} />
                  )}
                </div>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => choose(row, rowTitle)}
                >
                  {row.actionLabel}
                </Button>
              </li>
            );
          })}
        </ul>
      </PopoverContent>
    </Popover>
  );
}
