import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { DataFlowDiagram } from "./DataFlowDiagram";

describe("DataFlowDiagram", () => {
  it("모든 데이터 원천 노드 라벨을 렌더링한다", () => {
    render(<DataFlowDiagram />);
    expect(screen.getByText("개인 입력")).toBeInTheDocument();
    expect(screen.getByText("KOSIS CPI")).toBeInTheDocument();
    expect(screen.getByText("채용공고 데이터")).toBeInTheDocument();
    expect(screen.getByText("임금통계")).toBeInTheDocument();
  });

  it("잡코리아/사람인 실명을 렌더링하지 않는다", () => {
    render(<DataFlowDiagram />);
    expect(screen.queryByText(/잡코리아/)).not.toBeInTheDocument();
    expect(screen.queryByText(/사람인/)).not.toBeInTheDocument();
  });
});
