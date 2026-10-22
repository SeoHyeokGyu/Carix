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
});
