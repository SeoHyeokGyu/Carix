import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { describe, expect, it, vi } from "vitest";

import Root, { Layout } from "./root";

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return {
    ...actual,
    Meta: () => <meta data-testid="meta" />,
    Links: () => <link data-testid="links" />,
    Scripts: () => <script data-testid="scripts" />,
    ScrollRestoration: () => <div data-testid="scroll-restoration" />,
    Outlet: () => <div data-testid="outlet">아울렛 내용</div>,
  };
});

describe("루트 컴포넌트 (root)", () => {
  it("Layout이 메타데이터, 폰트 링크, 스크립트 및 자식 요소를 렌더링한다", () => {
    render(
      <Layout>
        <div data-testid="child">테스트 자식 엘리먼트</div>
      </Layout>,
    );

    expect(screen.getByTestId("child")).toBeInTheDocument();
    expect(screen.getByTestId("scroll-restoration")).toBeInTheDocument();
    expect(document.querySelector('[data-testid="meta"]')).not.toBeNull();
    expect(document.querySelector('[data-testid="links"]')).not.toBeNull();
  });

  it("Root가 Outlet을 렌더링한다", () => {
    render(
      <MemoryRouter>
        <Root />
      </MemoryRouter>,
    );

    expect(screen.getByTestId("outlet")).toBeInTheDocument();
  });
});
