import { describe, expect, it } from "vitest";

import routes from "./routes";

interface RouteEntry {
  id?: string;
  path?: string;
  file?: string;
}

describe("라우트 설정 (routes)", () => {
  it("최상위 라우트 및 레이아웃 목록을 올바르게 정의한다", () => {
    expect(Array.isArray(routes)).toBe(true);
    expect(routes.length).toBeGreaterThan(0);

    // home, login, register, protected 레이아웃(그 안에 onboarding/dashboard/mypage 3개가
    // 중첩된다), 4개 관리자 라우트(admin, admin/data, admin/collector, admin/pipeline), 3개 개발 프리뷰 라우트
    expect(routes).toHaveLength(11);
  });

  it("관리자 콘솔 라우트와 별칭(admin/collector, admin/pipeline)이 동일한 화면 파일을 연결한다", () => {
    const routeEntries = routes as unknown as RouteEntry[];
    const collectorRoute = routeEntries.find(
      (r) => r.path === "admin/collector",
    );
    const pipelineRoute = routeEntries.find((r) => r.path === "admin/pipeline");

    expect(collectorRoute).toBeDefined();
    expect(pipelineRoute).toBeDefined();
    expect(collectorRoute?.file).toBe("routes/admin/collector.tsx");
    expect(pipelineRoute?.file).toBe("routes/admin/collector.tsx");
    expect(pipelineRoute?.id).toBe("admin-pipeline");
  });

  it("S-19(admin/data-health) 경로는 없다", () => {
    const paths = (routes as unknown as RouteEntry[]).map((r) => r.path);
    expect(paths).not.toContain("admin/data-health");
  });
});
