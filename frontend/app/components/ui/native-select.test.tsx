import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { NativeSelect } from "./native-select";

describe("NativeSelect", () => {
  it("options 배열과 placeholder를 올바르게 렌더링한다", () => {
    const options = [
      { value: "a", label: "옵션 A" },
      { value: "b", label: "옵션 B" },
    ];

    render(
      <NativeSelect
        aria-label="선택 상자"
        placeholder="선택하세요"
        options={options}
      />,
    );

    const select = screen.getByLabelText("선택 상자");
    expect(select).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "선택하세요" })).toHaveValue("");
    expect(screen.getByRole("option", { name: "옵션 A" })).toHaveValue("a");
    expect(screen.getByRole("option", { name: "옵션 B" })).toHaveValue("b");
  });

  it("children을 직접 전달할 때도 올바르게 렌더링한다", async () => {
    const onChange = vi.fn();
    render(
      <NativeSelect aria-label="직접 옵션" onChange={onChange}>
        <option value="1">하나</option>
        <option value="2">둘</option>
      </NativeSelect>,
    );

    const select = screen.getByLabelText("직접 옵션");
    await userEvent.selectOptions(select, "2");
    expect(onChange).toHaveBeenCalled();
  });
});
