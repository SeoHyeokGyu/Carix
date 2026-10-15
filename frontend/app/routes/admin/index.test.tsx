import { describe, expect, it } from "vitest";

import { clientLoader } from "./index";

describe("admin index", () => {
  it("clientLoader 가 /admin/data 로 redirect 한다", () => {
    const res = clientLoader() as Response;
    expect(res.status).toBe(302);
    expect(res.headers.get("Location")).toBe("/admin/data");
  });
});
