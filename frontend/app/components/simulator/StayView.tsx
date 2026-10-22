import { Button } from "@/components/ui/button";

import { StayResultStep } from "./StayResultStep";
import type { Evaluation } from "./useEvaluation";

interface EvaluationFallbackProps {
  error: string | null;
  pending: boolean;
  onRetry: () => void;
}

/** 아직 보일 결과가 없을 때 결과 자리. 실패면 오류와 다시 시도, 아니면 계산 중. */
export function EvaluationFallback({
  error,
  pending,
  onRetry,
}: EvaluationFallbackProps) {
  if (error) {
    return (
      <div className="flex flex-col items-start gap-3">
        <p role="alert" className="text-sm text-carix-danger">
          {error}
        </p>
        <Button type="button" variant="outline" onClick={onRetry}>
          다시 시도
        </Button>
      </div>
    );
  }
  return (
    <p role="status" className="text-sm text-muted-foreground">
      {pending ? "계산하는 중입니다." : "결과를 불러오는 중입니다."}
    </p>
  );
}

interface StayViewProps extends EvaluationFallbackProps {
  evaluation: Evaluation;
  raisePercent: number;
  onFill: (code: string) => void;
  onCompare: () => void;
  onSave: (name: string | null) => Promise<void>;
}

/** ② 남으면. 결과가 오기 전에는 대체 표시, 온 뒤에는 재계산 중·실패 문장을 결과 위에 얹는다. */
export function StayView({
  evaluation,
  error,
  pending,
  raisePercent,
  onRetry,
  onFill,
  onCompare,
  onSave,
}: StayViewProps) {
  if (!evaluation.result) {
    return (
      <EvaluationFallback error={error} pending={pending} onRetry={onRetry} />
    );
  }
  return (
    <>
      {pending && (
        <p role="status" className="text-sm text-muted-foreground">
          다시 계산하는 중
        </p>
      )}
      {error && (
        <p role="alert" className="text-sm text-carix-danger">
          {error}
        </p>
      )}
      <StayResultStep
        result={evaluation.result}
        raisePercent={raisePercent}
        onFill={onFill}
        onCompare={onCompare}
        onSave={onSave}
        saveBlocked={pending}
      />
    </>
  );
}
