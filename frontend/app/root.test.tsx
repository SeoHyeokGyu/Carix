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

vi.mock("./lib/SessionProvider", () => ({
  SessionProvider: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="session-provider">{children}</div>
  ),
}));

describe("root", () => {
  it("renders Layout with children and head elements", () => {
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

  it("renders Root wrapping Outlet with SessionProvider", () => {
    render(
      <MemoryRouter>
        <Root />
      </MemoryRouter>,
    );

    expect(screen.getByTestId("session-provider")).toBeInTheDocument();
    expect(screen.getByTestId("outlet")).toBeInTheDocument();
  });
});
