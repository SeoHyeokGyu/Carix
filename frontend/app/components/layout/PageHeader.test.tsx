import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router";
import { describe, expect, it } from "vitest";
import { PageHeader } from "./PageHeader";

const renderIn = (ui: ReactNode) => render(<MemoryRouter>{ui}</MemoryRouter>);

describe("PageHeader", () => {
  it("제목은 표준 h1 하나이고 설명이 아래에 온다", () => {
    renderIn(<PageHeader title="추이 분석" description="설명 한 줄" />);
    const h1 = screen.getByRole("heading", { level: 1, name: "추이 분석" });
    expect(h1).toHaveClass(
      "font-display",
      "text-2xl",
      "sm:text-3xl",
      "font-bold",
    );
    expect(screen.getAllByRole("heading")).toHaveLength(1);
    expect(screen.getByText("설명 한 줄")).toHaveClass("text-sm");
  });

  it("루트는 data-page-header 를 단 header 이고 구분선은 선택이다", () => {
    const { container, rerender } = renderIn(<PageHeader title="판정" />);
    const root = container.querySelector("header");
    expect(root).toHaveAttribute("data-page-header");
    expect(root).not.toHaveClass("border-b");
    rerender(
      <MemoryRouter>
        <PageHeader title="판정" divider />
      </MemoryRouter>,
    );
    expect(container.querySelector("header")).toHaveClass("border-b", "pb-4");
  });

  it("back 이 있으면 제목 위에 상위 화면 링크를 둔다", () => {
    renderIn(
      <PageHeader
        title="지난 판정"
        back={{ to: "/dashboard", label: "판정" }}
      />,
    );
    const link = screen.getByRole("link", { name: "판정" });
    expect(link).toHaveAttribute("href", "/dashboard");
    expect(
      link.compareDocumentPosition(screen.getByRole("heading", { level: 1 })) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("back 이 없으면 링크가 없다", () => {
    renderIn(<PageHeader title="판정" />);
    expect(screen.queryByRole("link")).toBeNull();
  });

  it("actions 를 제목 줄 오른쪽 묶음에 그린다", () => {
    renderIn(
      <PageHeader
        title="판정"
        actions={<button type="button">지표 재계산</button>}
      />,
    );
    expect(
      screen.getByRole("button", { name: "지표 재계산" }).parentElement,
    ).toHaveClass("flex", "items-center", "gap-4");
  });
});
