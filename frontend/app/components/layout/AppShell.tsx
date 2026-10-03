import type { LucideIcon } from "lucide-react";
import { Link, NavLink, Outlet } from "react-router";
import { ThemeToggle } from "../ThemeToggle";
import { AccountMenu } from "./AccountMenu";

export interface NavItem {
  to: string;
  label: string;
  end?: boolean;
  icon?: LucideIcon;
}

interface AppShellProps {
  navLabel: string;
  items: NavItem[];
  utilityItems?: NavItem[];
  /** 설정·로그아웃 계정 메뉴. 온보딩 껍데기는 끈다. */
  accountMenu?: boolean;
  children?: React.ReactNode;
}

/** 세션 이후 화면(온보딩·대시보드·마이페이지)의 공통 껍데기. */
export function AppShell({
  navLabel,
  items,
  utilityItems,
  accountMenu = false,
  children,
}: AppShellProps) {
  return (
    <div className="min-h-screen bg-carix-bg text-carix-text transition-colors duration-300">
      <header className="sticky top-0 z-40 border-b border-carix-border bg-carix-surface/80 backdrop-blur-md">
        <div className="mx-auto flex w-full max-w-5xl items-center justify-between gap-4 px-6 py-2.5">
          {/* 좌측: 로고 + 주 내비게이션 */}
          <div className="flex items-center gap-4 md:gap-6 overflow-x-auto min-w-0">
            <Link
              to="/dashboard"
              className="flex items-center font-display text-xl font-extrabold tracking-tight shrink-0 select-none hover:opacity-90 transition-opacity"
            >
              <span className="bg-gradient-to-r from-carix-info to-carix-positive bg-clip-text text-transparent">
                Carix
              </span>
            </Link>

            {items.length > 0 && (
              <nav
                aria-label={navLabel}
                className="flex items-center gap-1 overflow-x-auto py-0.5"
              >
                {items.map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.end}
                    className={({ isActive }) =>
                      [
                        "whitespace-nowrap rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
                        isActive
                          ? "bg-carix-info/10 text-carix-info font-semibold"
                          : "text-carix-text/70 hover:bg-carix-border/40 hover:text-carix-text",
                      ].join(" ")
                    }
                  >
                    {item.label}
                  </NavLink>
                ))}
              </nav>
            )}
          </div>

          {/* 우측: 유틸리티 (도움말) + 계정 메뉴 */}
          <div className="flex items-center gap-2 shrink-0">
            {utilityItems?.map((uItem) => (
              <NavLink
                key={uItem.to}
                to={uItem.to}
                end={uItem.end}
                className={({ isActive }) =>
                  [
                    "inline-flex items-center gap-1.5 whitespace-nowrap rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
                    isActive
                      ? "bg-carix-info/10 text-carix-info font-semibold"
                      : "text-carix-text/70 hover:bg-carix-border/40 hover:text-carix-text",
                  ].join(" ")
                }
              >
                {uItem.icon && <uItem.icon aria-hidden className="size-4" />}
                <span>{uItem.label}</span>
              </NavLink>
            ))}
            {accountMenu && <AccountMenu />}
          </div>
        </div>
      </header>
      <ThemeToggle />
      <main>{children ?? <Outlet />}</main>
    </div>
  );
}
