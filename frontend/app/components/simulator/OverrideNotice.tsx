import { formatKoreanSalary } from "../../lib/koreanSalary";

/** "이번만 바꾸기" 연봉이 계산에 쓰이는 동안 결과 위에 보인다. 없으면 그리지 않는다. */
export function OverrideNotice({
  gross,
}: {
  gross: number | null | undefined;
}) {
  if (gross == null) return null;
  return (
    <p role="note" className="text-sm text-muted-foreground">
      이번만 바꾼 연봉 {formatKoreanSalary(gross)} 적용 중
    </p>
  );
}
