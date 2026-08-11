import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { IndexCrossoverLine } from "./IndexCrossoverLine";

function mockMatchMedia(matches: boolean) {
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockImplementation((query: string) => ({
      matches,
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("IndexCrossoverLine", () => {
  it("구매력(하락)과 시장가치(상승) 선 2개를 렌더링한다", () => {
    mockMatchMedia(false);
    render(<IndexCrossoverLine />);
    const svg = screen.getByTestId("index-crossover-line");
    expect(svg.querySelectorAll("path")).toHaveLength(2);
  });

  it("reduced motion이 아니면 data-reduced-motion=false다", () => {
    mockMatchMedia(false);
    render(<IndexCrossoverLine />);
    expect(screen.getByTestId("index-crossover-line")).toHaveAttribute(
      "data-reduced-motion",
      "false",
    );
  });

  it("reduced motion이면 data-reduced-motion=true다", () => {
    mockMatchMedia(true);
    render(<IndexCrossoverLine />);
    expect(screen.getByTestId("index-crossover-line")).toHaveAttribute(
      "data-reduced-motion",
      "true",
    );
  });
});
