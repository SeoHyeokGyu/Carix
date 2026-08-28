import { Link } from "react-router";
import { BETA_NOTICE } from "../../content/landing";

export function BetaNotice() {
  return (
    <footer aria-labelledby="beta-notice-heading" className="px-6 py-16">
      <div className="mx-auto max-w-3xl border-t border-carix-border pt-10 text-center">
        <h2
          id="beta-notice-heading"
          className="font-display text-lg font-semibold text-carix-text"
        >
          시작하기
        </h2>
        <p className="mt-4 text-sm leading-relaxed text-carix-text/70">
          {BETA_NOTICE}
        </p>
        <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
          <Link
            to="/register"
            className="rounded-lg bg-carix-info px-5 py-2.5 font-medium text-white transition-opacity hover:opacity-90"
          >
            가입하고 시작하기
          </Link>
          <Link
            to="/login"
            className="rounded-lg border border-carix-border px-5 py-2.5 font-medium text-carix-text transition-colors hover:bg-carix-border/30"
          >
            로그인
          </Link>
        </div>
      </div>
    </footer>
  );
}
