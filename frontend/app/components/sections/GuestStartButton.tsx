import { useState } from "react";
import { useNavigate } from "react-router";

import { Button } from "@/components/ui/button";
import { InlineAlert } from "@/components/ui/inline-alert";
import { cn } from "@/lib/utils";

import { toUserMessage } from "../../lib/apiError";
import { startGuest } from "../../lib/authApi";
import { stepPath } from "../../lib/routeGuards";
import { setSession } from "../../lib/session";

/** 랜딩의 게스트 시작 버튼. 히어로와 하단 "시작하기" 두 곳에 같은 동작으로 둔다. */
export function GuestStartButton({ className }: { className?: string }) {
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
    <div className={cn("flex flex-col items-center gap-3", className)}>
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
  );
}
