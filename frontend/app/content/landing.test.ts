import { describe, expect, it } from "vitest";

import { DATA_FLOW_NODES } from "./landing";

describe("landing content", () => {
  it("데이터 원천 노드에 잡코리아/사람인 실명을 노출하지 않는다", () => {
    const allText = DATA_FLOW_NODES.map((n) => `${n.label} ${n.detail}`).join(
      " ",
    );
    expect(allText).not.toContain("잡코리아");
    expect(allText).not.toContain("사람인");
    expect(allText).toContain("채용공고 데이터");
  });
});
