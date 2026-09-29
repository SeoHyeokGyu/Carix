/**
 * `S-10` 3층의 「나와 비교한 사람들」. 설계 §5.
 *
 * 서버 `appliedConditions` 는 원시 enum 이 섞인 조립 문자열이라 쪼개 번역하지 않는다 —
 * 구분자에 기대면 서버 문구가 바뀔 때 조용히 깨진다. 완화가 없을 때만 프로필 값으로
 * 칩을 새로 조립하고, 완화가 걸렸거나 조립할 재료가 모자라면 서버 문자열을 그대로 쓴다.
 */
import type { PeerGroupMetadata } from "./api/dashboard";
import type { ProfileResponse } from "./api/profile";
import { COMPANY_SIZES, EMPLOYMENT_TYPES } from "./schemas/profile";
import type { Option } from "./schemas/options";

export type PeerConditionView =
  | { kind: "chips"; chips: string[] }
  | { kind: "server"; text: string; widened: boolean };

const EXACT_LEVEL = 1;
const COMPANY_SIZE_RELEASED_LEVEL = 2;
const ANY_COMPANY_SIZE = "회사규모 무관";

// NOTE: (cjkang) 서버 PeerGroupFallbackResolver.mapExperienceBracket·formatBracketYears 와
// 같은 경계다. 서버 구간이 바뀌면 여기도 바꿔야 한다.
export function experienceBracketLabel(years: number): string {
  if (years <= 3) return "0~3년";
  if (years <= 7) return "4~7년";
  return "8년+";
}

function labelOf(options: readonly Option[], value: string): string | null {
  return options.find((option) => option.value === value)?.label ?? null;
}

/** 서버가 프로필 조건을 하나도 풀지 않았는가. 회사규모가 없으면 1단계를 건너뛰어 2단계가 곧 완전 일치다. */
function isExactMatch(
  fallbackLevel: number | null,
  profile: ProfileResponse,
): boolean {
  if (fallbackLevel === EXACT_LEVEL) return true;
  return (
    fallbackLevel === COMPANY_SIZE_RELEASED_LEVEL &&
    profile.companySize === null
  );
}

function buildChips(profile: ProfileResponse): string[] | null {
  const { region, yearsOfExperience, employmentType, companySize } = profile;
  if (
    region === null ||
    yearsOfExperience === null ||
    employmentType === null
  ) {
    return null;
  }
  const employment = labelOf(EMPLOYMENT_TYPES, employmentType);
  const size =
    companySize === null
      ? ANY_COMPANY_SIZE
      : labelOf(COMPANY_SIZES, companySize);
  if (employment === null || size === null) return null;

  const regionChip = region.includes("구") ? region : `${region} 전체`;
  return [
    regionChip,
    experienceBracketLabel(yearsOfExperience),
    size,
    employment,
  ];
}

export function resolvePeerConditions(
  peerGroup: PeerGroupMetadata | null | undefined,
  profile: ProfileResponse | null,
): PeerConditionView | null {
  if (!peerGroup) return null;
  const text = peerGroup.appliedConditions;
  if (!profile) return { kind: "server", text, widened: false };
  if (!isExactMatch(peerGroup.fallbackLevel, profile)) {
    return { kind: "server", text, widened: true };
  }
  const chips = buildChips(profile);
  if (!chips) return { kind: "server", text, widened: false };
  return { kind: "chips", chips };
}
