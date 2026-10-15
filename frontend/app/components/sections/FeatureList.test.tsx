import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { FEATURES } from "../../content/landing";
import { FeatureList } from "./FeatureList";

describe("FeatureList", () => {
  it("6개 기능 항목을 모두 리스트로 렌더링한다", () => {
    render(<FeatureList />);
    const items = screen.getAllByRole("listitem");
    expect(items).toHaveLength(FEATURES.length);
    FEATURES.forEach((feature) => {
      expect(screen.getByText(feature)).toBeInTheDocument();
    });
  });
});
