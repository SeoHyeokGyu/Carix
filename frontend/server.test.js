import { test } from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { setTimeout as sleep } from "node:timers/promises";

const PORT = 3999;

async function withServer(run) {
  const child = spawn("node", ["server.js"], {
    env: { ...process.env, PORT: String(PORT) },
    stdio: "pipe",
  });
  try {
    for (let i = 0; i < 50; i++) {
      try {
        await fetch(`http://localhost:${PORT}/`);
        break;
      } catch {
        await sleep(100);
      }
    }
    await run();
  } finally {
    child.kill();
  }
}

test("사전 렌더링되지 않은 앱 경로는 SPA 셸을 200으로 반환한다", async () => {
  await withServer(async () => {
    for (const path of ["/login", "/onboarding/profile", "/dashboard/trends"]) {
      const res = await fetch(`http://localhost:${PORT}${path}`);
      assert.equal(res.status, 200, `${path} 는 셸을 받아야 한다`);
      assert.match(res.headers.get("content-type") ?? "", /text\/html/);
    }
  });
});

test("존재하지 않는 에셋은 404를 반환한다", async () => {
  await withServer(async () => {
    const res = await fetch(`http://localhost:${PORT}/assets/no-such-file.js`);
    assert.equal(res.status, 404);
  });
});
