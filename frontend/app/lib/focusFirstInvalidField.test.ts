import { describe, expect, it } from "vitest";
import { focusFirstInvalidField } from "./focusFirstInvalidField";

describe("focusFirstInvalidField", () => {
  it("aria-invalid=true 인 첫 번째 컨트롤로 포커스를 옮긴다", () => {
    document.body.innerHTML = `
      <form>
        <input id="a" aria-invalid="false" />
        <input id="b" aria-invalid="true" />
        <input id="c" aria-invalid="true" />
      </form>
    `;
    const form = document.querySelector("form");

    focusFirstInvalidField(form);

    expect(document.activeElement).toBe(document.getElementById("b"));
  });

  it("잘못된 필드가 없으면 아무것도 하지 않는다", () => {
    document.body.innerHTML = `<form><input id="a" aria-invalid="false" /></form>`;
    const form = document.querySelector("form");

    expect(() => focusFirstInvalidField(form)).not.toThrow();
    expect(document.activeElement).not.toBe(document.getElementById("a"));
  });

  it("form 이 null 이어도 안전하다", () => {
    expect(() => focusFirstInvalidField(null)).not.toThrow();
  });
});
