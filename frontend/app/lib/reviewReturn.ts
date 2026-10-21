/**
 * 검토 화면(S-09)의 「수정」으로 들어온 단계는 저장 후 다음 단계가 아니라 검토로
 * 돌아간다(기능 명세 US-O6). 들어온 경로를 쿼리 하나로 나른다.
 */
export const REVIEW_PATH = "/onboarding/review";

const FROM_PARAM = "from";
const FROM_REVIEW = "review";

export function withReturnToReview(path: string): string {
  return `${path}?${FROM_PARAM}=${FROM_REVIEW}`;
}

export function isReturnToReview(url: string): boolean {
  return new URL(url).searchParams.get(FROM_PARAM) === FROM_REVIEW;
}
