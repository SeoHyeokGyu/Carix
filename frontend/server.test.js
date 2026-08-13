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

test("GET / 는 사전 렌더링된 HTML을 200으로 반환한다", async () => {
  await withServer(async () => {
    const res = await fetch(`http://localhost:${PORT}/`);
    assert.equal(res.status, 200);
    const body = await res.text();
    assert.match(body, /Carix/);
  });
});

test("존재하지 않는 경로는 404를 반환한다", async () => {
  await withServer(async () => {
    const res = await fetch(`http://localhost:${PORT}/no-such-path`);
    assert.equal(res.status, 404);
  });
});
