import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as SessionProviderModule from "../../lib/SessionProvider";
import type { Session } from "../../lib/session";
import MypageLayout from "./layout";
import MypageProfile from "./profile";
import MypageRecords from "./records";

vi.mock("../../lib/SessionProvider", () => ({
  useSession: vi.fn(),
}));

describe("Mypage routes and layout", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("pages", () => {
    it("renders MypageProfile", () => {
      render(<MypageProfile />);
      expect(screen.getByText("S-13")).toBeInTheDocument();
      expect(screen.getByRole("heading", { name: "설정" })).toBeInTheDocument();
    });

    it("renders MypageRecords", () => {
      render(<MypageRecords />);
      expect(screen.getByText("S-12")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "이력 관리" }),
      ).toBeInTheDocument();
    });
  });

  describe("MypageLayout", () => {
    it("renders SessionPending when session is loading", () => {
      vi.mocked(SessionProviderModule.useSession).mockReturnValue({
        status: "loading",
      });

      render(
        <MemoryRouter initialEntries={["/mypage/profile"]}>
          <MypageLayout />
        </MemoryRouter>,
      );

      expect(screen.getByRole("status")).toHaveTextContent("불러오는 중…");
    });

    it("redirects when session is anonymous", () => {
      vi.mocked(SessionProviderModule.useSession).mockReturnValue({
        status: "anonymous",
      });

      render(
        <MemoryRouter initialEntries={["/mypage/profile"]}>
          <Routes>
            <Route path="/mypage/profile" element={<MypageLayout />} />
            <Route path="/" element={<div>랜딩 페이지</div>} />
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.getByText("랜딩 페이지")).toBeInTheDocument();
    });

    it("renders AppShell with mypage tabs when authenticated", () => {
      const session: Session = {
        status: "authenticated",
        userId: "user-1",
        email: "user@carix.kr",
        onboardingStep: "PROFILE", // Even incomplete onboarding is allowed in mypage
      };
      vi.mocked(SessionProviderModule.useSession).mockReturnValue(session);

      render(
        <MemoryRouter initialEntries={["/mypage/profile"]}>
          <Routes>
            <Route element={<MypageLayout />}>
              <Route
                path="/mypage/profile"
                element={<div>설정 페이지 내용</div>}
              />
            </Route>
          </Routes>
        </MemoryRouter>,
      );

      expect(
        screen.getByRole("navigation", { name: "마이페이지" }),
      ).toBeInTheDocument();
      expect(screen.getByText("설정 페이지 내용")).toBeInTheDocument();
    });
  });
});
