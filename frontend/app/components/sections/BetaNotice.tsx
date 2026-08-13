import { BETA_NOTICE } from "../../content/landing";

export function BetaNotice() {
  return (
    <footer aria-labelledby="beta-notice-heading" className="px-6 py-16">
      <div className="mx-auto max-w-3xl border-t border-carix-border pt-10 text-center">
        <h2
          id="beta-notice-heading"
          className="font-display text-lg font-semibold text-carix-text"
        >
          비공개 베타 안내
        </h2>
        <p className="mt-4 text-sm leading-relaxed text-carix-text/70">
          {BETA_NOTICE}
        </p>
      </div>
    </footer>
  );
}
