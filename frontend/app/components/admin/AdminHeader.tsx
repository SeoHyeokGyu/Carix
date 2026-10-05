import { Link, NavLink } from "react-router";

const TABS = [
  { to: "/admin/data-health", label: "데이터 상태", id: "data-health" },
  { to: "/admin/collector", label: "수집 파이프라인", id: "collector" },
] as const;

/** 관리자 두 페이지 공통 헤더 — 탭과 사용자 화면 링크. */
export function AdminHeader({
  active,
}: {
  active: "data-health" | "collector";
}) {
  return (
    <header className="border-b border-carix-border bg-carix-surface/80 backdrop-blur-md sticky top-0 z-40">
      <div className="mx-auto flex w-full max-w-7xl items-center justify-between px-6 py-3">
        <nav aria-label="관리자" className="flex items-center gap-3">
          <Link
            to="/"
            className="font-bold text-base tracking-tight text-carix-text hover:text-carix-info"
          >
            Carix
          </Link>
          <span className="text-carix-border font-light">/</span>
          {TABS.map((t) => (
            <NavLink
              key={t.id}
              to={t.to}
              aria-current={active === t.id ? "page" : undefined}
              className={
                active === t.id
                  ? "rounded-md px-3 py-1.5 text-sm font-semibold text-carix-info bg-carix-info/10"
                  : "rounded-md px-3 py-1.5 text-sm text-carix-text/70 hover:bg-carix-border/40"
              }
            >
              {t.label}
            </NavLink>
          ))}
        </nav>
        <Link
          to="/dashboard"
          className="rounded-md px-3 py-1.5 text-xs text-carix-text/70 hover:bg-carix-border/40"
        >
          사용자 대시보드
        </Link>
      </div>
    </header>
  );
}
