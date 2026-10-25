import { Button } from "@/components/ui/button";

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
        <Button
          type="button"
          variant="outline"
          className="min-h-11"
          onClick={onRetry}
        >
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
