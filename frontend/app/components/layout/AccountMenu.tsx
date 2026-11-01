import {
  ChevronDown,
  ClipboardPen,
  LogOut,
  Settings,
  UserRound,
} from "lucide-react";
import { DropdownMenu } from "radix-ui";
import { useState } from "react";
import { Link, useLocation, useNavigate, useSubmit } from "react-router";

import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { InlineAlert } from "@/components/ui/inline-alert";
import { cn } from "@/lib/utils";

import { toUserMessage } from "../../lib/apiError";
import { logout } from "../../lib/authApi";
import { withNext } from "../../lib/nextPath";
import { REGISTER_PATH } from "../../lib/routeGuards";
import { ANONYMOUS, getSession, setSession } from "../../lib/session";

const MYPAGE_PATH = "/mypage";
const RECORDS_PATH = "/mypage/records";
const SETTINGS_PATH = "/mypage/profile";

// 좁은 화면은 터치라 항목·트리거 높이를 44px 로 맞춘다.
const ITEM_CLASS =
  "flex cursor-pointer items-center gap-2 rounded-sm px-2 py-3 sm:py-1.5 text-sm text-carix-text outline-none data-[highlighted]:bg-carix-border/40";

/**
 * 계정 메뉴 — 입력 관리·설정·로그아웃을 담는다.
 *
 * 입력 관리(S-12)는 처음 한 번 넣고 바뀔 때만 고치는 화면이라 주 메뉴가 아니라 여기 둔다.
 * 바뀌는 순간의 진입은 화면 안 링크(대시보드 입력값 「수정」, 스킬 분석 「보유 스킬 관리」,
 * 시뮬레이터 안내)가 맡는다. 상단 네비의 두 번째 층은 이 메뉴 하나뿐이다.
 *
 * 로그아웃은 설정 화면의 `clientAction`(intent=logout)으로 보낸다 — 세션 캐시 비우기와
 * 리다이렉트를 모든 기기 로그아웃과 같은 자리에서 처리한다.
 */
export function AccountMenu() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const session = getSession();
  const isGuest = session?.status === "authenticated" && session.guest;

  // 게스트는 마이페이지 가드(가입으로 보냄)를 거치지 않고 여기서 바로 끝낸다.
  const [menuOpen, setMenuOpen] = useState(false);
  const [endError, setEndError] = useState<string | null>(null);
  const endTrial = async () => {
    // 확인창이 열린 채 메뉴가 남으면 실패 알림이 가려진다.
    setMenuOpen(false);
    setEndError(null);
    try {
      await logout();
    } catch (error) {
      // 세션이 서버에 남아 있으니 끝난 것처럼 이동하지 않고 알린다.
      setEndError(toUserMessage(error));
      return;
    }
    setSession(ANONYMOUS);
    navigate("/", { replace: true });
  };
  const isActive = pathname.startsWith(MYPAGE_PATH);

  return (
    <>
      {endError && (
        // 좁은 화면에서 max-w-sm(384px)이 뷰포트보다 넓어 왼쪽으로 잘렸다 — 양쪽 여백으로 묶는다.
        <div className="fixed top-14 right-4 left-4 z-50 rounded-md border border-carix-border bg-carix-surface p-3 shadow-md sm:left-auto sm:max-w-sm">
          <InlineAlert message={endError} onDismiss={() => setEndError(null)} />
        </div>
      )}
      <DropdownMenu.Root open={menuOpen} onOpenChange={setMenuOpen}>
        <DropdownMenu.Trigger asChild>
          <Button
            variant="ghost"
            size="sm"
            aria-label="내 정보"
            data-active={isActive}
            className={cn(
              "h-11 sm:h-7",
              isActive
                ? "bg-carix-info/10 text-carix-info font-semibold"
                : "text-carix-text/70 hover:bg-carix-border/40 hover:text-carix-text",
            )}
          >
            <UserRound aria-hidden />
            <span className="hidden sm:inline">내 정보</span>
            <ChevronDown aria-hidden className="size-3.5" />
          </Button>
        </DropdownMenu.Trigger>
        <DropdownMenu.Portal>
          <DropdownMenu.Content
            align="end"
            sideOffset={6}
            className="z-50 min-w-40 rounded-md border border-carix-border bg-carix-surface p-1 shadow-md"
          >
            {isGuest ? (
              <>
                <DropdownMenu.Item asChild className={ITEM_CLASS}>
                  <Link to={withNext(REGISTER_PATH, pathname)}>
                    <UserRound aria-hidden className="size-4" />
                    가입해서 저장
                  </Link>
                </DropdownMenu.Item>
                <DropdownMenu.Separator className="my-1 h-px bg-carix-border" />
                <ConfirmDialog
                  trigger={
                    <DropdownMenu.Item
                      className={ITEM_CLASS}
                      onSelect={(event) => event.preventDefault()}
                    >
                      <LogOut aria-hidden className="size-4" />
                      체험 끝내기
                    </DropdownMenu.Item>
                  }
                  title="체험을 끝낼까요?"
                  description="입력한 내용은 복구할 수 없습니다."
                  confirmLabel="체험 끝내기"
                  tone="danger"
                  onConfirm={endTrial}
                />
              </>
            ) : (
              <MemberItems />
            )}
          </DropdownMenu.Content>
        </DropdownMenu.Portal>
      </DropdownMenu.Root>
    </>
  );
}

function MemberItems() {
  const submit = useSubmit();
  return (
    <>
      <DropdownMenu.Item asChild className={ITEM_CLASS}>
        <Link to={RECORDS_PATH}>
          <ClipboardPen aria-hidden className="size-4" />
          입력 관리
        </Link>
      </DropdownMenu.Item>
      <DropdownMenu.Item asChild className={ITEM_CLASS}>
        <Link to={SETTINGS_PATH}>
          <Settings aria-hidden className="size-4" />
          설정
        </Link>
      </DropdownMenu.Item>
      <DropdownMenu.Separator className="my-1 h-px bg-carix-border" />
      <DropdownMenu.Item
        className={ITEM_CLASS}
        onSelect={() =>
          submit(
            { intent: "logout" },
            { method: "post", action: SETTINGS_PATH },
          )
        }
      >
        <LogOut aria-hidden className="size-4" />
        로그아웃
      </DropdownMenu.Item>
    </>
  );
}
