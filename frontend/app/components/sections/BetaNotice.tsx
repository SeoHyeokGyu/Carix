import { useState } from "react";
import { Link, useNavigate } from "react-router";

import { Button } from "@/components/ui/button";
import { InlineAlert } from "@/components/ui/inline-alert";

import { BETA_NOTICE } from "../../content/landing";
import { toUserMessage } from "../../lib/apiError";
import { startGuest } from "../../lib/authApi";
import { stepPath } from "../../lib/routeGuards";
import { setSession } from "../../lib/session";

export function BetaNotice() {
  const navigate = useNavigate();
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleStartGuest() {
    setError(null);
    setStarting(true);
    try {
      const response = await startGuest();
      // 랜딩은 protected 레이아웃 밖이라 세션 캐시가 비어 있다 — 목적지 가드가
      // 비로그인으로 보고 되튕기지 않게 먼저 채운다.
      setSession({ status: "authenticated", ...response });
      void navigate(stepPath(response.onboardingStep), { replace: true });
    } catch (caught) {
      setError(toUserMessage(caught));
      setStarting(false);
    }
  }

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
        <div className="mt-8 flex flex-col items-center gap-3">
          <Button
            type="button"
            size="lg"
            disabled={starting}
            onClick={() => void handleStartGuest()}
          >
            {starting ? "시작하는 중…" : "가입 없이 시작하기"}
          </Button>
          {error ? <InlineAlert message={error} /> : null}
        </div>
        <div className="mt-4 flex flex-wrap items-center justify-center gap-3">
          <Link
            to="/register"
            className="rounded-lg border border-carix-border px-5 py-2.5 font-medium text-carix-text transition-colors hover:bg-carix-border/30"
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
