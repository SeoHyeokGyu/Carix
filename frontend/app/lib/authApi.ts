import type { SessionResponse } from "./session";

/**
 * 인증 API 호출.
 *
 * 세션은 HTTP-only 쿠키라 자바스크립트가 읽지 못한다. 모든 요청에 `credentials: "include"`
 * 가 필요하다 — 빼면 쿠키가 실려 나가지 않아 로그인해도 계속 비로그인으로 보인다.
 */
const JSON_HEADERS = { "Content-Type": "application/json" };

export interface ApiError {
  code: string;
  message: string;
}

async function post(path: string, body: unknown): Promise<SessionResponse> {
  const response = await fetch(`/api/v1/auth/${path}`, {
    method: "POST",
    credentials: "include",
    headers: JSON_HEADERS,
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    throw await toApiError(response);
  }
  return (await response.json()) as SessionResponse;
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as Partial<ApiError>;
    if (body.message) {
      return { code: body.code ?? "UNKNOWN", message: body.message };
    }
  } catch {
    // 서버가 JSON 을 주지 못한 경우(502·타임아웃 등)는 아래 기본 문구로 떨어진다.
  }
  return {
    code: "UNKNOWN",
    message: "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
  };
}

export function register(email: string, password: string) {
  return post("register", { email, password });
}

export function login(email: string, password: string) {
  return post("login", { email, password });
}

export async function logout(): Promise<void> {
  await fetch("/api/v1/auth/logout", {
    method: "POST",
    credentials: "include",
  });
}

/** 세션이 없으면 `null`. 401 은 오류가 아니라 "비로그인"이라는 정상 응답이다. */
export async function fetchSession(): Promise<SessionResponse | null> {
  const response = await fetch("/api/v1/auth/session", {
    credentials: "include",
  });
  if (response.status === 401) return null;
  if (!response.ok) throw await toApiError(response);
  return (await response.json()) as SessionResponse;
}
