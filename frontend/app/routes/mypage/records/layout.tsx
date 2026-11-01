import { NavLink, Outlet } from "react-router";

import { PageFrame } from "@/components/layout/PageFrame";
import { PageHeader } from "@/components/layout/PageHeader";
import {
  TAB_ACTIVE,
  TAB_IDLE,
  TAB_ITEM,
  TAB_LIST,
} from "@/components/ui/tab-styles";
import { cn } from "@/lib/utils";

import type { NavItem } from "../../../components/layout/AppShell";

/**
 * 탭 라벨의 정본. 테스트와 E2E 가 여기서 읽는다.
 *
 * NOTE: (cjkang) 세션 가드는 상위 `routes/mypage/layout.tsx` 가 이미 건다. 여기에
 * `clientLoader` 를 두지 않는 이유 — 탭 네비게이션은 데이터가 필요 없고, 가드를 두
 * 겹으로 두면 어느 쪽이 튕겼는지 추적이 어려워진다.
 */
export const RECORDS_TAB_ITEMS: NavItem[] = [
  { to: "/mypage/records", label: "소득", end: true },
  { to: "/mypage/records/expense", label: "소비" },
  { to: "/mypage/records/skills", label: "스킬" },
];

export default function RecordsLayout() {
  return (
    <PageFrame>
      <PageHeader
        title="입력 관리"
        description="소득·소비·스킬 이력을 고치면 그 시점 이후 지수가 다시 계산됩니다."
      />

      <nav aria-label="이력 종류" className={cn("mb-6", TAB_LIST)}>
        {RECORDS_TAB_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              cn(TAB_ITEM, isActive ? TAB_ACTIVE : TAB_IDLE)
            }
          >
            {item.label}
          </NavLink>
        ))}
      </nav>

      <Outlet />
    </PageFrame>
  );
}
