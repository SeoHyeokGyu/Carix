import type { LucideIcon } from "lucide-react";
import { Link, NavLink, Outlet, useLocation } from "react-router";
import { ThemeToggle } from "../ThemeToggle";
import { AccountMenu } from "./AccountMenu";

export interface NavItem {
  to: string;
  label: string;
  end?: boolean;
  icon?: LucideIcon;
  /** 이 항목을 선택된 것으로 볼 하위 화면 경로. 정확히 같거나 그 아래면 선택이다. */
  match?: readonly string[];
}

const NAV_ACTIVE = "bg-carix-info/10 text-carix-info font-semibold";
const NAV_IDLE =
  "text-carix-text/70 hover:bg-carix-border/40 hover:text-carix-text";
const NAV_ITEM =
  "whitespace-nowrap rounded-md px-3 py-1.5 text-sm font-medium transition-colors";

function isInSection(item: NavItem, pathname: string): boolean {
  return (
    item.match?.some((p) => pathname === p || pathname.startsWith(`${p}/`)) ??
    false
  );
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
  const { pathname } = useLocation();
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
                {items.map((item) =>
                  // NavLink 는 자기가 선택일 때만 aria-current 를 붙인다 — 하위 화면에서는
                  // Link 로 그려 「이 묶음 안」(true)을 직접 단다.
                  isInSection(item, pathname) ? (
                    <Link
                      key={item.to}
                      to={item.to}
                      aria-current="true"
                      className={`${NAV_ITEM} ${NAV_ACTIVE}`}
                    >
                      {item.label}
                    </Link>
                  ) : (
                    <NavLink
                      key={item.to}
                      to={item.to}
                      end={item.end}
                      className={({ isActive }) =>
                        `${NAV_ITEM} ${isActive ? NAV_ACTIVE : NAV_IDLE}`
                      }
                    >
                      {item.label}
                    </NavLink>
                  ),
                )}
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
                  `inline-flex items-center gap-1.5 ${NAV_ITEM} ${isActive ? NAV_ACTIVE : NAV_IDLE}`
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
