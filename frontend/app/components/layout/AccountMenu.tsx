import {
  ChevronDown,
  ClipboardPen,
  LogOut,
  Settings,
  UserRound,
} from "lucide-react";
import { DropdownMenu } from "radix-ui";
import { Link, useLocation, useSubmit } from "react-router";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

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
  const submit = useSubmit();
  const isActive = pathname.startsWith(MYPAGE_PATH);

  return (
    <DropdownMenu.Root>
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
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}
