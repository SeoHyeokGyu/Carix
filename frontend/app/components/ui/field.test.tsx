import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Field } from "./field";

describe("Field", () => {
  it("라벨과 자식 입력 요소를 렌더링하고 기본 접근성 속성을 바인딩한다", () => {
    render(
      <Field id="test-input" label="테스트 라벨">
        {(aria) => <input {...aria} />}
      </Field>,
    );

    const input = screen.getByLabelText("테스트 라벨");
    expect(input).toBeInTheDocument();
    expect(input).toHaveAttribute("id", "test-input");
    expect(input).toHaveAttribute("aria-invalid", "false");
    expect(input).not.toHaveAttribute("aria-describedby");
  });

  it("힌트 텍스트가 있을 때 aria-describedby에 힌트 id를 연결한다", () => {
    render(
      <Field id="test-input" label="테스트 라벨" hint="안내 문구">
        {(aria) => <input {...aria} />}
      </Field>,
    );

    const input = screen.getByLabelText("테스트 라벨");
    expect(screen.getByText("안내 문구")).toBeInTheDocument();
    expect(input).toHaveAttribute("aria-describedby", "test-input-hint");
  });

  it("에러 문구가 있을 때 aria-invalid와 aria-describedby를 설정한다", () => {
    render(
      <Field
        id="test-input"
        label="테스트 라벨"
        hint="안내 문구"
        error="오류 발생"
      >
        {(aria) => <input {...aria} />}
      </Field>,
    );

    const input = screen.getByLabelText("테스트 라벨");
    const errorMsg = screen.getByRole("alert");
    expect(errorMsg).toHaveTextContent("오류 발생");
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveAttribute(
      "aria-describedby",
      "test-input-hint test-input-error",
    );
  });
});
