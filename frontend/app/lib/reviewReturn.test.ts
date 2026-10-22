import { describe, expect, it } from "vitest";

import { isReturnToReview, withReturnToReview } from "./reviewReturn";

describe("reviewReturn", () => {
  it("검토에서 들어온 경로를 표시하고 다시 읽는다", () => {
    const path = withReturnToReview("/onboarding/income");

    expect(path).toBe("/onboarding/income?from=review");
    expect(isReturnToReview(`http://localhost${path}`)).toBe(true);
  });
});
