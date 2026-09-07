/**
 * core-api 호출 래퍼.
 *
 * core-api 는 응답을 `ApiResult<T>` 로 감싼다 (`{success, data, message, timestamp}`).
 * auth-service 는 감싸지 않으므로 그쪽은 `authApi.ts` 를 그대로 쓴다.
 *
 * 401 을 한곳에서 다루는 이유는 세션 게이트(개발 계획 §5-B) 때문이다. 플래그를 켜는 순간
 * 세션 없는 모든 호출이 401 이 되므로 처리 지점이 흩어져 있으면 안 된다.
 */
const JSON_HEADERS = { "Content-Type": "application/json" };

export interface ApiError {
  code: string;
  message: string;
  status: number;
}

/** 401 전용. loader 가 이것을 잡아 로그인으로 보낸다. */
export class UnauthorizedError extends Error {
  constructor() {
    super("세션이 없습니다");
    this.name = "UnauthorizedError";
  }
}

interface ApiResult<T> {
  success: boolean;
  data: T;
  message?: string;
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as Partial<ApiError>;
    if (body.message) {
      return {
        code: body.code ?? "UNKNOWN",
        message: body.message,
        status: response.status,
      };
    }
  } catch {
    // 서버가 JSON 을 주지 못한 경우(502·타임아웃 등)는 아래 기본 문구로 떨어진다.
  }
  return {
    code: "UNKNOWN",
    message: "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
    status: response.status,
  };
}

async function unwrap<T>(response: Response): Promise<T> {
  if (response.status === 401) {
    throw new UnauthorizedError();
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  if (response.status === 204) {
    return null as T;
  }
  const body = (await response.json()) as ApiResult<T>;
  return body.data;
}

export async function coreGet<T>(path: string): Promise<T> {
  const response = await fetch(`/api${path}`, {
    method: "GET",
    credentials: "include",
    headers: JSON_HEADERS,
  });
  return unwrap<T>(response);
}

export async function coreSend<T>(
  method: "POST" | "PUT" | "PATCH" | "DELETE",
  path: string,
  body?: unknown,
): Promise<T> {
  const response = await fetch(`/api${path}`, {
    method,
    credentials: "include",
    headers: JSON_HEADERS,
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  return unwrap<T>(response);
}
