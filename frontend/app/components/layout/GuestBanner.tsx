import { TextLink } from "@/components/ui/text-link";

import { withNext } from "../../lib/nextPath";
import { REGISTER_PATH } from "../../lib/routeGuards";

/** 게스트 체험 중임을 알리고 가입 뒤 지금 화면으로 돌아오는 가입 링크를 건넨다. */
export function GuestBanner({ pathname }: { pathname: string }) {
  return (
    <div
      role="status"
      className="flex flex-wrap items-center justify-center gap-x-3 gap-y-1 border-b border-carix-border bg-carix-info/10 px-6 py-2 text-sm text-carix-text"
    >
      <span>체험 중입니다. 입력한 내용은 24시간 뒤 사라집니다.</span>
      <TextLink
        to={withNext(REGISTER_PATH, pathname)}
        direction="none"
        size="sm"
        className="font-semibold"
      >
        가입해서 저장
      </TextLink>
    </div>
  );
}
