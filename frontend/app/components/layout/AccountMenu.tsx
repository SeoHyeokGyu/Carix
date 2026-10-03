import { ChevronDown, LogOut, Settings, UserRound } from "lucide-react";
import { DropdownMenu } from "radix-ui";
import { Link, useLocation, useSubmit } from "react-router";
import { Button } from "@/components/ui/button";

const SETTINGS_PATH = "/mypage/profile";

const ITEM_CLASS =
  "flex cursor-pointer items-center gap-2 rounded-sm px-2 py-1.5 text-sm text-carix-text outline-none data-[highlighted]:bg-carix-border/40";

/**
 * 계정 메뉴 — 설정·로그아웃만 담는다.
 *
 * 입력 관리(S-12)는 여기 넣지 않는다. 연봉·소비 갱신이 스냅샷 재계산과 시나리오 사후 검증의
 * 입구라 주 메뉴에서 한 번에 보여야 한다. 상단 네비의 두 번째 층은 이 메뉴 하나뿐이다.
 *
 * 로그아웃은 설정 화면의 `clientAction`(intent=logout)으로 보낸다 — 세션 캐시 비우기와
 * 리다이렉트를 모든 기기 로그아웃과 같은 자리에서 처리한다.
 */
export function AccountMenu() {
  const { pathname } = useLocation();
  const submit = useSubmit();
  const isActive = pathname.startsWith(SETTINGS_PATH);

  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>
        <Button
          variant="ghost"
          size="sm"
          aria-label="내 정보"
          data-active={isActive}
          className={
            isActive
              ? "bg-carix-info/10 text-carix-info font-semibold"
              : "text-carix-text/70 hover:bg-carix-border/40 hover:text-carix-text"
          }
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
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}
