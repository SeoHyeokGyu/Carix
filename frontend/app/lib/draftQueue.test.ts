import { describe, expect, it } from "vitest";

import type { EvaluateRequest } from "./api/scenario";
import { createDraftQueue } from "./draftQueue";

const req = (tag: string) => ({ tag }) as unknown as EvaluateRequest;

function deferred() {
  let resolve!: () => void;
  let reject!: (e: unknown) => void;
  const promise = new Promise<void>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

describe("createDraftQueue", () => {
  it("늦게 끝난 앞선 저장이 뒤 저장보다 먼저 보내지고 뒤 저장이 마지막이다", async () => {
    const A = req("A");
    const B = req("B");
    const first = deferred();
    const calls: EvaluateRequest[] = [];
    const put = (r: EvaluateRequest) => {
      calls.push(r);
      return calls.length === 1 ? first.promise : Promise.resolve();
    };
    const q = createDraftQueue(put);

    const pa = q(A);
    const pb = q(B);
    await Promise.resolve();
    await Promise.resolve();
    expect(calls).toEqual([A]);

    first.resolve();
    await Promise.all([pa, pb]);
    expect(calls).toEqual([A, B]);
  });

  it("앞 저장 실패가 뒤 저장을 막지 않는다", async () => {
    const A = req("A");
    const B = req("B");
    const calls: EvaluateRequest[] = [];
    const put = (r: EvaluateRequest) => {
      calls.push(r);
      return r === A ? Promise.reject(new Error("fail")) : Promise.resolve();
    };
    const q = createDraftQueue(put);

    const pa = q(A);
    const pb = q(B);
    await expect(pa).rejects.toThrow("fail");
    await expect(pb).resolves.toBeUndefined();
    expect(calls).toEqual([A, B]);
  });
});
