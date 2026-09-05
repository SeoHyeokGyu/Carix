import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AuthForm } from "./AuthForm";

describe("AuthForm", () => {
  const defaultProps = {
    screenId: "S-02",
    title: "로그인",
    description: "계정이 있으면 이어서 봅니다.",
    submitLabel: "로그인하기",
    passwordAutoComplete: "current-password" as const,
    onSubmit: vi.fn().mockResolvedValue(undefined),
    footer: <span>푸터 텍스트</span>,
  };

  it("화면 ID, 제목, 설명, 푸터 및 제출 버튼을 정상 렌더링한다", () => {
    render(<AuthForm {...defaultProps} />);

    expect(screen.getByText("S-02")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "로그인" })).toBeInTheDocument();
    expect(
      screen.getByText("계정이 있으면 이어서 봅니다."),
    ).toBeInTheDocument();
    expect(screen.getByText("푸터 텍스트")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "로그인하기" }),
    ).toBeInTheDocument();
  });

  it("note 속성이 주어지면 참고 문구를 렌더링한다", () => {
    const { rerender } = render(<AuthForm {...defaultProps} />);
    expect(screen.queryByText("비밀번호 분실 주의")).not.toBeInTheDocument();

    rerender(<AuthForm {...defaultProps} note="비밀번호 분실 주의" />);
    expect(screen.getByText("비밀번호 분실 주의")).toBeInTheDocument();
  });

  it("이메일과 비밀번호를 입력하고 폼을 제출하면 onSubmit 핸들러를 호출한다", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<AuthForm {...defaultProps} onSubmit={onSubmit} />);

    const emailInput = screen.getByLabelText("이메일");
    const passwordInput = screen.getByLabelText(/비밀번호/);
    const submitButton = screen.getByRole("button", { name: "로그인하기" });

    fireEvent.change(emailInput, { target: { value: "test@carix.kr" } });
    fireEvent.change(passwordInput, { target: { value: "pass1234!" } });

    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith("test@carix.kr", "pass1234!");
    });
  });

  it("ApiError 예외 발생 시 에러 메시지를 alert 역할로 노출한다", async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue({ message: "이메일 또는 비밀번호 불일치" });
    render(<AuthForm {...defaultProps} onSubmit={onSubmit} />);

    const emailInput = screen.getByLabelText("이메일");
    const passwordInput = screen.getByLabelText(/비밀번호/);
    const submitButton = screen.getByRole("button", { name: "로그인하기" });

    fireEvent.change(emailInput, { target: { value: "wrong@carix.kr" } });
    fireEvent.change(passwordInput, { target: { value: "wrongpass" } });

    fireEvent.click(submitButton);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("이메일 또는 비밀번호 불일치");
    expect(submitButton).not.toBeDisabled();
    expect(submitButton).toHaveTextContent("로그인하기");
  });

  it("메시지가 없는 에러 발생 시 기본 안내 문구를 alert 역할로 노출한다", async () => {
    const onSubmit = vi.fn().mockRejectedValue({});
    render(<AuthForm {...defaultProps} onSubmit={onSubmit} />);

    const emailInput = screen.getByLabelText("이메일");
    const passwordInput = screen.getByLabelText(/비밀번호/);
    const submitButton = screen.getByRole("button", { name: "로그인하기" });

    fireEvent.change(emailInput, { target: { value: "wrong@carix.kr" } });
    fireEvent.change(passwordInput, { target: { value: "wrongpass" } });

    fireEvent.click(submitButton);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("요청을 처리하지 못했습니다.");
    expect(submitButton).not.toBeDisabled();
  });

  it("제출 중에는 버튼을 비활성화하고 처리 중 문구를 표시한다", async () => {
    let resolveSubmit: () => void = () => {};
    const pendingPromise = new Promise<void>((resolve) => {
      resolveSubmit = resolve;
    });
    const onSubmit = vi.fn().mockReturnValue(pendingPromise);

    render(<AuthForm {...defaultProps} onSubmit={onSubmit} />);

    fireEvent.change(screen.getByLabelText("이메일"), {
      target: { value: "slow@carix.kr" },
    });
    fireEvent.change(screen.getByLabelText(/비밀번호/), {
      target: { value: "slowpassword" },
    });

    const submitButton = screen.getByRole("button", { name: "로그인하기" });
    fireEvent.click(submitButton);

    expect(submitButton).toBeDisabled();
    expect(submitButton).toHaveTextContent("처리 중…");

    // Complete the submission
    resolveSubmit();
    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalled();
    });
  });
});
