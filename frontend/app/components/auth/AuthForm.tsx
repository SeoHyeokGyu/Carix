import { useState, type FormEvent, type ReactNode } from "react";
import type { ApiError } from "../../lib/authApi";
import { Button } from "@/components/ui/button";

interface AuthFormProps {
  screenId: string;
  title: string;
  description: string;
  submitLabel: string;
  /** 비밀번호 입력의 autocomplete 힌트. 가입은 `new-password`, 로그인은 `current-password`. */
  passwordAutoComplete: "new-password" | "current-password";
  onSubmit: (email: string, password: string) => Promise<void>;
  footer: ReactNode;
  note?: ReactNode;
}

/** 가입과 로그인이 같은 폼을 쓴다. 다른 것은 문구와 제출 동작뿐이다. */
export function AuthForm({
  screenId,
  title,
  description,
  submitLabel,
  passwordAutoComplete,
  onSubmit,
  footer,
  note,
}: AuthFormProps) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await onSubmit(email, password);
    } catch (caught) {
      setError((caught as ApiError).message ?? "요청을 처리하지 못했습니다.");
      setSubmitting(false);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-carix-bg px-6 py-16">
      <section
        aria-labelledby={`${screenId}-heading`}
        className="w-full max-w-sm rounded-2xl border border-carix-border bg-carix-surface p-8 shadow-sm"
      >
        <p className="font-display text-xs font-semibold tracking-widest text-carix-info">
          {screenId}
        </p>
        <h1
          id={`${screenId}-heading`}
          className="mt-2 font-display text-2xl font-bold text-carix-text"
        >
          {title}
        </h1>
        <p className="mt-2 text-sm leading-relaxed text-carix-text/70">
          {description}
        </p>

        <form onSubmit={handleSubmit} className="mt-8 flex flex-col gap-4">
          <label className="flex flex-col gap-1.5 text-sm font-medium text-carix-text">
            이메일
            <input
              type="email"
              name="email"
              required
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="rounded-lg border border-carix-border bg-carix-bg px-3 py-2 text-base font-normal text-carix-text outline-none focus:border-carix-info focus:ring-2 focus:ring-carix-info/30"
            />
          </label>

          <label className="flex flex-col gap-1.5 text-sm font-medium text-carix-text">
            비밀번호
            <input
              type="password"
              name="password"
              required
              minLength={8}
              autoComplete={passwordAutoComplete}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="rounded-lg border border-carix-border bg-carix-bg px-3 py-2 text-base font-normal text-carix-text outline-none focus:border-carix-info focus:ring-2 focus:ring-carix-info/30"
            />
            <span className="text-xs font-normal text-carix-text/60">
              8자 이상. 문자 구성은 제한하지 않습니다.
            </span>
          </label>

          {error ? (
            <p
              role="alert"
              className="rounded-lg bg-carix-danger/10 px-3 py-2 text-sm text-carix-danger"
            >
              {error}
            </p>
          ) : null}

          <Button
            type="submit"
            size="lg"
            disabled={submitting}
            className="mt-2"
          >
            {submitting ? "처리 중…" : submitLabel}
          </Button>
        </form>

        <p className="mt-6 text-sm text-carix-text/70">{footer}</p>
        {note ? (
          <p className="mt-3 text-xs leading-relaxed text-carix-text/50">
            {note}
          </p>
        ) : null}
      </section>
    </main>
  );
}
