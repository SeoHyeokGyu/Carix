import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router";
import { describe, expect, it } from "vitest";
import { TextLink } from "./text-link";

function renderLink(ui: ReactNode) {
  return render(<MemoryRouter>{ui}</MemoryRouter>);
}

const hasTextSize = (el: HTMLElement) =>
  [...el.classList].some((c) => /^text-(xs|sm|base|lg)$/.test(c));

describe("TextLink", () => {
  it("forward 는 글자 뒤에 화살표를 두고 마우스 올림에만 밑줄이다", () => {
    renderLink(<TextLink to="/dashboard/history">지난 판정</TextLink>);

    const link = screen.getByRole("link", { name: "지난 판정" });
    expect(link).toHaveAttribute("href", "/dashboard/history");
    expect(link.lastElementChild?.tagName.toLowerCase()).toBe("svg");
    expect(link.lastElementChild).toHaveAttribute("aria-hidden", "true");
    expect(link).toHaveClass("hover:underline", "text-xs", "text-carix-info");
    expect(link).not.toHaveClass("underline");
  });

  it("back 은 글자 앞에 화살표를 둔다", () => {
    renderLink(
      <TextLink to="/dashboard" direction="back" size="sm">
        판정
      </TextLink>,
    );

    const link = screen.getByRole("link", { name: "판정" });
    expect(link.firstElementChild?.tagName.toLowerCase()).toBe("svg");
    expect(link.querySelectorAll("svg")).toHaveLength(1);
    expect(link).toHaveClass("hover:underline", "text-sm");
  });

  // 문장 속·표 안 링크는 화살표가 없어 색만으로 갈린다 — 늘 밑줄이어야 한다(WCAG 1.4.1).
  it("none 은 화살표 없이 늘 밑줄이다", () => {
    renderLink(
      <TextLink to="/register" direction="none" size="sm">
        가입하기
      </TextLink>,
    );

    const link = screen.getByRole("link", { name: "가입하기" });
    expect(link.querySelector("svg")).toBeNull();
    expect(link).toHaveClass("underline");
    expect(link).not.toHaveClass("hover:underline");
  });

  it("inherit 은 글자 크기를 정하지 않아 부모 크기를 따른다", () => {
    renderLink(
      <TextLink to="/admin/data" direction="none" size="inherit">
        202609
      </TextLink>,
    );

    expect(hasTextSize(screen.getByRole("link", { name: "202609" }))).toBe(
      false,
    );
  });

  it("Link 속성과 className 을 그대로 넘긴다", () => {
    renderLink(
      <TextLink
        to="/dashboard/trends?tab=income"
        aria-label="구매력 지수 자세히 보기"
        title="이 달 공고 보기"
        className="self-start"
      >
        자세히 보기
      </TextLink>,
    );

    const link = screen.getByRole("link", { name: "구매력 지수 자세히 보기" });
    expect(link).toHaveAttribute("href", "/dashboard/trends?tab=income");
    expect(link).toHaveAttribute("title", "이 달 공고 보기");
    expect(link).toHaveClass("self-start");
  });
});
