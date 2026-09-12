import { StepIndicator, type StepNumber } from "./StepIndicator";

interface StepBadgesProps {
  current: StepNumber;
}

/**
 * 온보딩 진행 단계 표시 컴포넌트 (StepIndicator 위임)
 */
export function StepBadges({ current }: StepBadgesProps) {
  return <StepIndicator current={current} />;
}
