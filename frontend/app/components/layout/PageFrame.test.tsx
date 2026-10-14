import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PageFrame } from "./PageFrame";

describe("PageFrame", () => {
  it("기본은 헤더와 같은 5xl 폭·좌우 여백이다", () => {
    render(<PageFrame data-testid="f">내용</PageFrame>);
    const f = screen.getByTestId("f");
    expect(f.tagName).toBe("DIV");
    expect(f).toHaveClass("mx-auto", "w-full", "max-w-5xl", "px-6", "py-8");
    expect(f).toHaveAttribute("data-page-frame", "wide");
  });

  // 헤더까지 좁히면 오른쪽 동작이 3xl 선에 붙는다 — 헤더는 제한에서 뺀다.
  it("narrow 는 헤더를 뺀 자식만 3xl 로 제한한다", () => {
    render(
      <PageFrame width="narrow" data-testid="f">
        내용
      </PageFrame>,
    );
    const f = screen.getByTestId("f");
    expect(f).toHaveClass("max-w-5xl");
    expect(f.className).toContain("[&>*:not([data-page-header])]:max-w-3xl");
  });

  it("admin 은 관리자 헤더와 같은 7xl 이고 as 로 main 을 쓴다", () => {
    render(
      <PageFrame width="admin" as="main" aria-busy>
        내용
      </PageFrame>,
    );
    const f = screen.getByRole("main");
    expect(f).toHaveClass("max-w-7xl");
    expect(f).not.toHaveClass("max-w-5xl");
    expect(f).toHaveAttribute("aria-busy", "true");
  });

  it("className 을 합친다", () => {
    render(
      <PageFrame data-testid="f" className="space-y-4">
        내용
      </PageFrame>,
    );
    expect(screen.getByTestId("f")).toHaveClass("space-y-4", "max-w-5xl");
  });
  // 탭 줄·오류 알림처럼 섹션이 아닌 형제 사이는 건드리지 않는다 — 섹션끼리만 32px.
  it("data-page-section 형제끼리만 32px 를 준다", () => {
    render(<PageFrame data-testid="f">내용</PageFrame>);
    const cls = screen.getByTestId("f").className;
    expect(cls).toContain("[&>[data-page-section]~[data-page-section]]:mt-8");
    expect(cls).not.toMatch(/\[&>\*[^\]]*\+\*\]:mt-8/);
  });
});
