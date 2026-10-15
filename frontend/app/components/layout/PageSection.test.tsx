import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PageSection } from "./PageSection";

describe("PageSection", () => {
  it("h2 제목으로 이름 붙은 section 이다", () => {
    render(
      <PageSection title="이 판정을 만든 지표">
        <p>내용</p>
      </PageSection>,
    );
    const region = screen.getByRole("region", { name: "이 판정을 만든 지표" });
    expect(region).toHaveAttribute("data-page-section");
    expect(region).toHaveClass("flex", "flex-col", "gap-3");
    expect(
      screen.getByRole("heading", { level: 2, name: "이 판정을 만든 지표" }),
    ).toHaveClass("font-display", "text-lg", "font-semibold");
  });

  it("설명과 actions 를 제목 줄에 둔다", () => {
    render(
      <PageSection
        title="섹션"
        description="설명"
        actions={<a href="#x">링크</a>}
      >
        내용
      </PageSection>,
    );
    expect(screen.getByText("설명")).toHaveClass("text-sm");
    expect(
      screen.getByRole("link", { name: "링크" }).parentElement,
    ).toHaveClass("flex", "items-center");
  });

  // 탭 패널이 이미 탭으로 이름을 갖는다 — 이름 붙은 section 을 또 두지 않는다.
  it("titleHidden 이면 sr-only h2 만 두고 region 이 아니다", () => {
    render(
      <PageSection title="소득 vs 물가" titleHidden>
        내용
      </PageSection>,
    );
    expect(screen.queryByRole("region")).toBeNull();
    const h = screen.getByRole("heading", { level: 2, name: "소득 vs 물가" });
    expect(h).toHaveClass("sr-only");
    expect(h.parentElement).toHaveAttribute("data-page-section");
  });
});
