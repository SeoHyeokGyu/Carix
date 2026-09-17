import { EmptyState } from "@/components/ui/empty-state";

export default function RecordsSkills() {
  return <p>스킬 이력</p>;
}

export function ErrorBoundary() {
  return (
    <EmptyState
      variant="plain"
      tone="danger"
      icon="!"
      title="스킬 이력을 불러오지 못했습니다"
      description="잠시 후 다시 시도해 주세요. 다른 탭은 그대로 열립니다."
    />
  );
}
