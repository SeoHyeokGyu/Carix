import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { SignalBadge } from "./SignalBadge";

describe("SignalBadge", () => {
  it("아는 코드의 캐노니컬 문구를 렌더링한다", () => {
    render(<SignalBadge code="SEEK_JOB" />);

    expect(screen.getByText("이직 탐색")).toBeInTheDocument();
  });

  it("모르는 코드는 서버 문구로 떨어진다", () => {
    render(<SignalBadge code="RELOCATE" serverLabel="지역 이동 검토" />);

    expect(screen.getByText("지역 이동 검토")).toBeInTheDocument();
  });
});
