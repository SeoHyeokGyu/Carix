import { NavLink, Outlet } from "react-router";
import { ThemeToggle } from "../ThemeToggle";

export interface NavItem {
  to: string;
  label: string;
  end?: boolean;
}

interface AppShellProps {
  navLabel: string;
  items: NavItem[];
}

/** 세션 이후 화면(온보딩·대시보드·마이페이지)의 공통 껍데기. */
export function AppShell({ navLabel, items }: AppShellProps) {
  return (
    <div className="min-h-screen bg-carix-bg text-carix-text transition-colors duration-300">
      <ThemeToggle />
      <header className="border-b border-carix-border bg-carix-surface/80 backdrop-blur-md">
        <nav
          aria-label={navLabel}
          className="mx-auto flex w-full max-w-5xl gap-1 overflow-x-auto px-6 py-3"
        >
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                [
                  "whitespace-nowrap rounded-md px-3 py-2 text-sm font-medium transition-colors",
                  isActive
                    ? "bg-carix-info/10 text-carix-info"
                    : "text-carix-text/70 hover:bg-carix-border/40 hover:text-carix-text",
                ].join(" ")
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}
