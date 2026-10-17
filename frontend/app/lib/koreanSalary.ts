/**
 * 원 단위 금액을 "X,XXX만 원" 또는 "X억 X,XXX만 원" 형태로 포맷
 */
export function formatKoreanSalary(won: number | null | undefined): string {
  if (won == null) return "-";
  if (won === 0) return "0원";

  const eok = Math.floor(won / 100_000_000);
  const man = Math.floor((won % 100_000_000) / 10_000);

  if (eok > 0 && man > 0) {
    return `${eok}억 ${man.toLocaleString()}만 원`;
  } else if (eok > 0) {
    return `${eok}억 원`;
  } else if (man > 0) {
    return `${man.toLocaleString()}만 원`;
  } else {
    return `${won.toLocaleString()}원`;
  }
}
