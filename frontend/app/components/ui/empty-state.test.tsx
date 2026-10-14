import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { EmptyState } from "./empty-state";

describe("EmptyState", () => {
  it("제목을 표제로 올린다", () => {
    render(<EmptyState title="기록된 월별 스냅샷이 없습니다." />);
    expect(
      screen.getByText("기록된 월별 스냅샷이 없습니다."),
    ).toBeInTheDocument();
  });

  it("설명과 아이콘은 있을 때만 그린다", () => {
    const { rerender } = render(<EmptyState title="비었음" />);
    expect(screen.queryByTestId("empty-state-icon")).not.toBeInTheDocument();

    rerender(
      <EmptyState title="비었음" icon="📋" description="곧 채워집니다" />,
    );
    expect(screen.getByTestId("empty-state-icon")).toHaveTextContent("📋");
    expect(screen.getByText("곧 채워집니다")).toBeInTheDocument();
  });

  it("children 을 행동 슬롯으로 받는다", () => {
    render(
      <EmptyState title="비었음">
        <button type="button">시작하기</button>
      </EmptyState>,
    );
    expect(
      screen.getByRole("button", { name: "시작하기" }),
    ).toBeInTheDocument();
  });

  it("tone 을 주면 아이콘이 그 톤의 알약이 된다", () => {
    render(<EmptyState title="비었음" icon="⚠️" tone="warning" />);
    expect(screen.getByTestId("empty-state-icon")).toHaveAttribute(
      "data-tone",
      "warning",
    );
  });

  it("plain 은 테두리와 배경을 벗는다 — 이미 카드 안일 때 쓴다", () => {
    const { rerender } = render(<EmptyState title="비었음" />);
    const boxed = screen.getByTestId("empty-state").className;
    expect(boxed).toContain("border-dashed");
    expect(boxed).toContain("bg-carix-surface");

    rerender(<EmptyState title="비었음" variant="plain" />);
    const plain = screen.getByTestId("empty-state").className;
    expect(plain).not.toContain("border-dashed");
    expect(plain).not.toContain("bg-carix-surface");
  });

  it("호출부가 data-testid 를 덮어쓸 수 있다 — 갤러리처럼 여러 개 놓일 때", () => {
    render(<EmptyState title="비었음" data-testid="gallery-empty" />);
    expect(screen.getByTestId("gallery-empty")).toBeInTheDocument();
    expect(screen.queryByTestId("empty-state")).not.toBeInTheDocument();
  });

  it("headingLevel 을 주면 그 태그로 제목을 그린다", () => {
    render(<EmptyState title="이력이 없습니다" headingLevel="h2" />);
    expect(
      screen.getByRole("heading", { level: 2, name: "이력이 없습니다" }),
    ).toBeInTheDocument();
  });

  it("headingLevel 이 없으면 제목은 heading 이 아니다", () => {
    render(<EmptyState title="이력이 없습니다" />);
    expect(screen.queryByRole("heading")).toBeNull();
    expect(screen.getByText("이력이 없습니다").tagName).toBe("P");
  });
});
