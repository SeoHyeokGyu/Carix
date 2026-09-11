# 개발자 C 3~4주차 구현 계획 (`c3`)

> **에이전트 실행자에게:** 이 계획은 `superpowers:subagent-driven-development` 또는
> `superpowers:executing-plans` 로 태스크 단위로 실행한다. 체크박스(`- [ ]`)가 진행 표시다.

**목표:** 온보딩 네 화면(`S-05`·`S-06`·`S-07`·`S-09`)·대시보드(`S-10`)·시뮬레이터(`S-15`)를
실데이터로 동작시키고, 그것을 막고 있는 온보딩 단계 갱신 경로를 `auth-service` 에 만든다.

**접근:** React Router 7 의 `clientLoader`/`clientAction` 을 데이터 계층으로 쓴다. 세션은 root
loader 가 잡고 진입 가드는 layout loader 에서 `redirect()` 로 처리한다. 검증은 Zod, 컴포넌트는
shadcn/ui. 백엔드 작업은 `auth-service` 온보딩 단계 갱신 하나뿐이고 나머지 API 는 이미 있다.

**기술 스택:** React 19, React Router 7 (framework mode, `ssr: false`), TypeScript, Tailwind v4,
shadcn/ui, Zod, Vitest + Testing Library, Playwright / Spring Boot (auth-service), JUnit 5 +
MockMvc + Mockito

**설계 문서:** [`carix-dev-plan-c3-design.md`](carix-dev-plan-c3-design.md) — 이 계획은 그
설계를 근거로 삼는다. 실행자는 둘을 함께 읽는다.

## 전역 제약

- **금액을 브라우저 저장소에 쓰지 않는다.** `localStorage`·`sessionStorage` 금지, 쿼리스트링
  금지, `console` 출력 금지 ([기능 명세 §7](../specs/functional-specification.md) 완료 기준 10)
- **`core-api` 응답은 `ApiResult<T>` 로 감싸여 있다** (`{success, data, message, timestamp}`).
  본문은 `data` 안에 있다. **`auth-service` 는 감싸지 않고 DTO 를 그대로 준다** — 두 규약이
  다르므로 `coreApi.ts` 만 벗겨 낸다
- 오류 응답은 `ErrorResponse` — `{code, message, timestamp, details}`
- 지수 값이 없을 때 0 이나 100 으로 채우지 않는다. `indexStatus` 로 표현한다
- 커밋 메시지는 한글 Conventional Commits. `Co-Authored-By` 트레일러와 세션 링크를 넣지 않는다
- 테스트 이름은 기존 파일 관례를 따른다 — 프런트엔드는 한글 서술문, 백엔드는 한글 + 밑줄
- 프런트엔드 검증 명령: `pnpm test:unit`, `pnpm typecheck`, `pnpm lint` (작업 디렉터리 `frontend/`)
- 백엔드 검증 명령: `./gradlew :auth-service:test`, `./gradlew :core-api:test` (작업 디렉터리 `backend/`)

---

## 파일 구조

### 새로 만드는 파일

| 파일 | 책임 |
| :--- | :--- |
| `backend/auth-service/.../user/OnboardingController.java` | `PATCH /v1/auth/onboarding` |
| `backend/auth-service/.../user/OnboardingUpdateRequest.java` | 단계 갱신 요청 (허용값 검증) |
| `backend/auth-service/.../user/OnboardingUpdateResponse.java` | 단계 갱신 응답 |
| `frontend/app/lib/coreApi.ts` | `core-api` 호출 래퍼 — `ApiResult` 벗기기, 오류 정규화, 401 처리 |
| `frontend/app/lib/onboardingApi.ts` | `PATCH /v1/auth/onboarding` 호출 |
| `frontend/app/lib/schemas/profile.ts` | `S-05` 입력 스키마 + 직군·지역 등 선택지 상수 |
| `frontend/app/lib/schemas/income.ts` | `S-06` 입력 스키마 |
| `frontend/app/lib/schemas/expense.ts` | `S-07` 입력 스키마 + 카테고리 상수 |
| `frontend/app/lib/schemas/scenario.ts` | `S-15` 입력 스키마 |
| `frontend/app/lib/api/profile.ts` | 프로필 조회·저장 |
| `frontend/app/lib/api/income.ts` | 소득 이력 CRUD |
| `frontend/app/lib/api/expense.ts` | 지출 조회·월 단위 저장 |
| `frontend/app/lib/api/dashboard.ts` | `GET /v1/dashboard/summary` |
| `frontend/app/lib/api/scenario.ts` | 시나리오 조회·계산 |
| `frontend/app/lib/api/userSkills.ts` | `GET /v1/user-skills` (읽기 전용, `S-09` 요약용) |
| `frontend/app/components/ui/*` | shadcn 컴포넌트 |
| `frontend/app/components/onboarding/StepBadges.tsx` | 온보딩 5단계 표시기 |
| `frontend/app/components/dashboard/IndexCard.tsx` | 지수 카드 + `indexStatus` 뱃지 |
| `frontend/app/components/expense/ExpenseDonut.tsx` | CSS `conic-gradient` 비중 도넛 |

### 고치는 파일

| 파일 | 변경 |
| :--- | :--- |
| `backend/auth-service/.../user/User.java` | 단계 변경 메서드 추가 |
| `backend/core-api/.../profile/ProfileController.java` | 온보딩 스텁 두 개 철거 |
| `backend/core-api/src/test/.../profile/OnboardingFlowTest.java` | 철거한 스텁 단언 제거 |
| `frontend/app/root.tsx` | `clientLoader` + `HydrateFallback` |
| `frontend/app/lib/SessionProvider.tsx` | 초기 세션을 props 로 받는다 |
| `frontend/app/routes/onboarding/layout.tsx` 외 2 | 렌더 가드 → loader 가드 |
| `frontend/app/routes/onboarding/{profile,income,expense,review}.tsx` | 실화면 |
| `frontend/app/routes/dashboard/{index,simulator}.tsx` | 실화면 |
| `frontend/tsconfig.json`, `frontend/vite.config.ts` | `@/` 별칭 |
| `frontend/app/styles/tokens.css` | shadcn 토큰 매핑 |

---

## Task 1: `auth-service` 온보딩 단계 갱신

설계 §3. 이것이 없으면 온보딩 네 화면이 전부 막힌다. `User` 에 변경 메서드가 없고
`UserRepository` 에 갱신 경로가 없으므로 엔티티부터 만든다.

**Files:**
- Modify: `backend/auth-service/src/main/java/com/carix/authservice/user/User.java`
- Create: `backend/auth-service/src/main/java/com/carix/authservice/user/OnboardingUpdateRequest.java`
- Create: `backend/auth-service/src/main/java/com/carix/authservice/user/OnboardingUpdateResponse.java`
- Create: `backend/auth-service/src/main/java/com/carix/authservice/user/OnboardingController.java`
- Test: `backend/auth-service/src/test/java/com/carix/authservice/user/UserTest.java` (추가)
- Test: `backend/auth-service/src/test/java/com/carix/authservice/user/OnboardingControllerTest.java`

**Interfaces:**
- Produces: `User.changeOnboardingStep(String step, OffsetDateTime now)` — 단계 문자열과 갱신
  시각을 받아 `onboardingStep`·`updatedAt` 을 바꾼다
- Produces: `PATCH /v1/auth/onboarding` — 요청 `{"currentStep": "INCOME"}`, 응답
  `{"currentStep": "INCOME", "completed": false}`. 쿠키가 없거나 세션이 무효면 `401`,
  허용되지 않는 단계 값이면 `400`

- [ ] **Step 1: `User` 변경 메서드의 실패 테스트를 쓴다**

`UserTest.java` 에 추가한다.

```java
  @Test
  void 온보딩_단계를_바꾸면_갱신시각도_함께_바뀐다() {
    OffsetDateTime created = OffsetDateTime.parse("2026-09-01T00:00:00Z");
    OffsetDateTime changed = OffsetDateTime.parse("2026-09-07T10:00:00Z");
    User user =
        new User(UUID.randomUUID(), "user@example.com", "hash", "PROFILE", created, created);

    user.changeOnboardingStep("INCOME", changed);

    assertThat(user.getOnboardingStep()).isEqualTo("INCOME");
    assertThat(user.getUpdatedAt()).isEqualTo(changed);
    assertThat(user.getCreatedAt()).isEqualTo(created);
  }
```

- [ ] **Step 2: 실패를 확인한다**

Run: `cd backend && ./gradlew :auth-service:test --tests '*UserTest'`
Expected: 컴파일 실패 — `changeOnboardingStep` 심볼을 찾을 수 없다

- [ ] **Step 3: `User` 에 메서드를 더한다**

`User.java` 의 `isOnboardingComplete()` 위에 넣는다.

```java
  /** 온보딩 단계를 옮긴다. 허용값 검증은 요청 경계(OnboardingUpdateRequest)에서 한다. */
  public void changeOnboardingStep(String step, OffsetDateTime now) {
    this.onboardingStep = step;
    this.updatedAt = now;
  }
```

- [ ] **Step 4: 통과를 확인한다**

Run: `cd backend && ./gradlew :auth-service:test --tests '*UserTest'`
Expected: PASS

- [ ] **Step 5: 컨트롤러의 실패 테스트를 쓴다**

`OnboardingControllerTest.java` 를 만든다. `SessionControllerTest` 와 같은 뼈대다.

```java
package com.carix.authservice.user;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.authservice.session.SessionCookies;
import com.carix.authservice.session.SessionService;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = OnboardingController.class)
class OnboardingControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-07T00:00:00Z");
  private static final String TOKEN = "a".repeat(64);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SessionService sessionService;
  @MockitoBean private UserRepository userRepository;

  private Cookie sessionCookie() {
    return new Cookie(SessionCookies.NAME, TOKEN);
  }

  private User user(String step) {
    return new User(USER_ID, "beta@example.com", "$2a$10$h", step, NOW, NOW);
  }

  @Test
  void 단계를_갱신하고_현재_단계를_돌려준다() throws Exception {
    given(sessionService.findUserIdBySessionToken(TOKEN)).willReturn(Optional.of(USER_ID));
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("PROFILE")));

    mockMvc
        .perform(
            patch("/v1/auth/onboarding")
                .cookie(sessionCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentStep\":\"INCOME\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentStep").value("INCOME"))
        .andExpect(jsonPath("$.completed").value(false));
  }

  @Test
  void COMPLETED_로_옮기면_완료로_표시한다() throws Exception {
    given(sessionService.findUserIdBySessionToken(TOKEN)).willReturn(Optional.of(USER_ID));
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("REVIEW")));

    mockMvc
        .perform(
            patch("/v1/auth/onboarding")
                .cookie(sessionCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentStep\":\"COMPLETED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completed").value(true));
  }

  @Test
  void 쿠키가_없으면_401_이다() throws Exception {
    mockMvc
        .perform(
            patch("/v1/auth/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentStep\":\"INCOME\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 허용되지_않는_단계값은_400_이다() throws Exception {
    mockMvc
        .perform(
            patch("/v1/auth/onboarding")
                .cookie(sessionCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentStep\":\"DONE\"}"))
        .andExpect(status().isBadRequest());
  }
}
```

- [ ] **Step 6: 실패를 확인한다**

Run: `cd backend && ./gradlew :auth-service:test --tests '*OnboardingControllerTest'`
Expected: 컴파일 실패 — `OnboardingController` 가 없다

- [ ] **Step 7: 요청·응답 레코드를 만든다**

`OnboardingUpdateRequest.java`:

```java
package com.carix.authservice.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 온보딩 단계 갱신 요청. 허용값을 여기서 막아 잘못된 단계가 users 에 들어가지 않게 한다. */
@Schema(description = "온보딩 단계 갱신 요청")
public record OnboardingUpdateRequest(
    @NotBlank
        @Pattern(regexp = "PROFILE|INCOME|EXPENSE|SKILLS|REVIEW|COMPLETED")
        @Schema(example = "INCOME")
        String currentStep) {}
```

`OnboardingUpdateResponse.java`:

```java
package com.carix.authservice.user;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "온보딩 단계 갱신 응답")
public record OnboardingUpdateResponse(
    @Schema(example = "INCOME") String currentStep, @Schema(example = "false") boolean completed) {

  public static OnboardingUpdateResponse from(User user) {
    return new OnboardingUpdateResponse(user.getOnboardingStep(), user.isOnboardingComplete());
  }
}
```

- [ ] **Step 8: 컨트롤러를 만든다**

```java
package com.carix.authservice.user;

import com.carix.authservice.session.SessionCookies;
import com.carix.authservice.session.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 온보딩 단계 갱신.
 *
 * <p>{@code users.onboarding_step} 은 auth-service 소유라 갱신도 여기서 한다. core-api 에 있던 같은 이름의
 * 엔드포인트는 아무것도 쓰지 않는 스텁이었고 철거했다 (개발 계획 §5-G).
 */
@RestController
@Tag(name = "auth", description = "세션 관리")
public class OnboardingController {

  private final SessionService sessionService;
  private final UserRepository userRepository;

  public OnboardingController(SessionService sessionService, UserRepository userRepository) {
    this.sessionService = sessionService;
    this.userRepository = userRepository;
  }

  @Operation(summary = "온보딩 단계 갱신", description = "세션 쿠키의 사용자에 대해 온보딩 진행 단계를 옮긴다.")
  @PatchMapping("/v1/auth/onboarding")
  public ResponseEntity<OnboardingUpdateResponse> update(
      @CookieValue(name = SessionCookies.NAME, required = false) String sessionToken,
      @Valid @RequestBody OnboardingUpdateRequest request) {

    if (sessionToken == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    return sessionService
        .findUserIdBySessionToken(sessionToken)
        .flatMap(userRepository::findById)
        .map(
            user -> {
              user.changeOnboardingStep(
                  request.currentStep(), OffsetDateTime.now(ZoneOffset.UTC));
              userRepository.save(user);
              return ResponseEntity.ok(OnboardingUpdateResponse.from(user));
            })
        .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
  }
}
```

- [ ] **Step 9: 통과를 확인한다**

Run: `cd backend && ./gradlew :auth-service:test`
Expected: 전부 PASS

- [ ] **Step 10: 커밋**

```bash
git add backend/auth-service
git commit -m "feat(auth-service): 온보딩 단계 갱신 API 추가

users.onboarding_step 은 auth-service 소유인데 갱신 경로가 없어
온보딩 화면이 단계를 진행시킬 수 없었다. 엔티티 변경 메서드와
PATCH /v1/auth/onboarding 을 더한다."
```

---

## Task 2: `core-api` 온보딩 스텁 철거

설계 §3. `GET`·`PATCH /v1/onboarding` 은 하드코딩 값을 돌려주고 아무것도 쓰지 않는다.
Task 1 이 대체했으므로 지운다.

**Files:**
- Modify: `backend/core-api/src/main/java/com/carix/profile/ProfileController.java`
- Delete: `backend/core-api/src/main/java/com/carix/profile/dto/OnboardingStatusRequest.java`
- Delete: `backend/core-api/src/main/java/com/carix/profile/dto/OnboardingStatusResponse.java`
- Modify: `backend/core-api/src/test/java/com/carix/profile/OnboardingFlowTest.java`

**Interfaces:**
- Consumes: Task 1 의 `PATCH /v1/auth/onboarding`
- Produces: `core-api` 에 `/v1/onboarding` 경로가 더 이상 없다

- [ ] **Step 1: 테스트에서 스텁 단언을 지운다**

`OnboardingFlowTest.java` 의 첫 블록(`get("/v1/onboarding")` 으로 `currentStep = EXPENSE` 를
단언하는 부분)과, 파일 끝에서 `patch("/v1/onboarding")` 을 부르는 블록이 있으면 함께 지운다.
남은 흐름(프로필 → 소득 → 지출 → 스킬)은 그대로 둔다. 시나리오 주석 번호를 이어서 다시 매긴다.

- [ ] **Step 2: 지금 실패하는지 확인한다**

Run: `cd backend && ./gradlew :core-api:test --tests '*OnboardingFlowTest'`
Expected: PASS (테스트만 줄인 상태라 통과한다. 여기서 실패하면 지운 범위가 넓다)

- [ ] **Step 3: 컨트롤러에서 엔드포인트 둘을 지운다**

`ProfileController.java` 에서 `getOnboardingStatus()`·`updateOnboardingStatus()` 메서드와 그
`@Operation`·`@ApiResponse` 주석을 지우고, 쓰이지 않게 된 `OnboardingStatusRequest`·
`OnboardingStatusResponse` import 를 지운다. 그다음 DTO 파일 둘을 삭제한다.

- [ ] **Step 4: 전체 테스트로 잔여 참조를 찾는다**

Run: `cd backend && ./gradlew :core-api:test`
Expected: PASS. 실패하면 지운 DTO 를 참조하는 곳이 남은 것이므로 함께 정리한다

- [ ] **Step 5: 커밋**

```bash
git add backend/core-api
git commit -m "refactor(core-api): 온보딩 단계 엔드포인트 스텁 철거

하드코딩 값을 돌려주고 아무것도 쓰지 않았다. auth-service 의
PATCH /v1/auth/onboarding 이 대체한다."
```

---

## Task 3: `coreApi.ts` — `core-api` 호출 래퍼

설계 §4.3. `ApiResult` 벗기기와 오류 정규화, 401 처리를 한곳에 모은다.

**Files:**
- Create: `frontend/app/lib/coreApi.ts`
- Test: `frontend/app/lib/coreApi.test.ts`

**Interfaces:**
- Produces: `coreGet<T>(path: string): Promise<T>`,
  `coreSend<T>(method: "POST"|"PUT"|"PATCH"|"DELETE", path: string, body?: unknown): Promise<T>`
- Produces: `ApiError` = `{ code: string; message: string; status: number }`
- Produces: `UnauthorizedError` — 401 일 때 던지는 클래스. loader 가 이것을 잡아 로그인으로 보낸다

- [ ] **Step 1: 실패 테스트를 쓴다**

```ts
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { coreGet, coreSend, UnauthorizedError } from "./coreApi";

describe("coreApi", () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("ApiResult 봉투를 벗겨 data 만 돌려준다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        success: true,
        data: { jobFamily: "DEVELOPER" },
        message: "요청이 성공적으로 처리되었습니다.",
      }),
    } as unknown as Response);

    await expect(coreGet("/v1/profile")).resolves.toEqual({
      jobFamily: "DEVELOPER",
    });
    expect(globalThis.fetch).toHaveBeenCalledWith("/api/v1/profile", {
      method: "GET",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
    });
  });

  it("204 응답은 null 로 돌려준다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 204,
      json: async () => {
        throw new Error("본문 없음");
      },
    } as unknown as Response);

    await expect(coreGet("/v1/scenarios/latest")).resolves.toBeNull();
  });

  it("401 이면 UnauthorizedError 를 던진다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      json: async () => ({ code: "UNAUTHORIZED", message: "인증이 필요합니다." }),
    } as unknown as Response);

    await expect(coreGet("/v1/profile")).rejects.toBeInstanceOf(
      UnauthorizedError,
    );
  });

  it("ErrorResponse 의 code 와 message 를 ApiError 로 옮긴다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 409,
      json: async () => ({
        code: "DUPLICATE_EFFECTIVE_DATE",
        message: "같은 유효 시작일이 이미 있습니다.",
      }),
    } as unknown as Response);

    await expect(
      coreSend("POST", "/v1/income", { grossSalary: 1 }),
    ).rejects.toEqual({
      code: "DUPLICATE_EFFECTIVE_DATE",
      message: "같은 유효 시작일이 이미 있습니다.",
      status: 409,
    });
  });

  it("JSON 이 아닌 오류 응답에는 기본 문구를 쓴다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 502,
      json: async () => {
        throw new Error("not json");
      },
    } as unknown as Response);

    await expect(coreGet("/v1/profile")).rejects.toMatchObject({
      code: "UNKNOWN",
      status: 502,
    });
  });

  it("본문이 있는 요청은 JSON 으로 직렬화해 보낸다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ success: true, data: { id: "1" } }),
    } as unknown as Response);

    await coreSend("PUT", "/v1/expenses/202609", { items: [] });

    expect(globalThis.fetch).toHaveBeenCalledWith("/api/v1/expenses/202609", {
      method: "PUT",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ items: [] }),
    });
  });
});
```

- [ ] **Step 2: 실패를 확인한다**

Run: `cd frontend && pnpm test:unit app/lib/coreApi.test.ts`
Expected: FAIL — `coreApi` 모듈을 찾을 수 없다

- [ ] **Step 3: 구현한다**

```ts
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
```

- [ ] **Step 4: 통과를 확인한다**

Run: `cd frontend && pnpm test:unit app/lib/coreApi.test.ts && pnpm typecheck`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add frontend/app/lib/coreApi.ts frontend/app/lib/coreApi.test.ts
git commit -m "feat(frontend): core-api 호출 래퍼 추가

ApiResult 봉투 벗기기, 오류 정규화, 401 단일 처리."
```

---

## Task 4: 세션을 root loader 로 올리고 가드를 loader 로 옮긴다

설계 §4.1. `ssr: false` 라 root 에 `clientLoader` 를 두면 `HydrateFallback` 이 필요하다.

**Files:**
- Modify: `frontend/app/root.tsx`
- Modify: `frontend/app/lib/SessionProvider.tsx`
- Modify: `frontend/app/routes/onboarding/layout.tsx`
- Modify: `frontend/app/routes/dashboard/layout.tsx`
- Modify: `frontend/app/routes/mypage/layout.tsx`
- Test: `frontend/app/lib/SessionProvider.test.tsx` (수정)
- Test: `frontend/app/routes/onboarding/onboarding.test.tsx` (수정)

**Interfaces:**
- Consumes: `fetchSession()` (`authApi.ts`), `guardOnboarding`·`guardOnboarded`·`guardSession`
  (`routeGuards.ts`) — 시그니처 그대로 쓴다
- Produces: root `clientLoader` 가 `{ session: Session }` 를 돌려준다
- Produces: `SessionProvider` 가 `initialSession: Session` prop 을 받는다

- [ ] **Step 1: `SessionProvider` 의 새 계약을 실패 테스트로 쓴다**

`SessionProvider.test.tsx` 의 "마운트 시 세션을 조회한다" 류 테스트를 아래로 바꾼다.

```tsx
  it("초기 세션을 props 로 받아 조회 없이 그대로 노출한다", () => {
    const fetchSpy = vi.spyOn(authApi, "fetchSession");
    const initial: Session = {
      status: "authenticated",
      userId: "u-1",
      email: "a@b.kr",
      onboardingStep: "INCOME",
    };

    render(
      <SessionProvider initialSession={initial}>
        <Probe />
      </SessionProvider>,
    );

    expect(screen.getByTestId("step")).toHaveTextContent("INCOME");
    expect(fetchSpy).not.toHaveBeenCalled();
  });
```

`Probe` 는 같은 파일 안에 둔다.

```tsx
function Probe() {
  const session = useSession();
  return (
    <span data-testid="step">
      {session.status === "authenticated" ? session.onboardingStep : session.status}
    </span>
  );
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `cd frontend && pnpm test:unit app/lib/SessionProvider.test.tsx`
Expected: FAIL — `initialSession` prop 이 없어 `fetchSession` 이 호출된다

- [ ] **Step 3: `SessionProvider` 를 고친다**

`useState(LOADING)` 을 `useState(initialSession)` 으로 바꾸고 마운트 `useEffect` 와 그
`eslint-disable` 주석을 지운다. `refresh` 는 남긴다 — 로그인·로그아웃·단계 갱신 뒤에 쓴다.

```tsx
export function SessionProvider({
  initialSession,
  children,
}: {
  initialSession: Session;
  children: ReactNode;
}) {
  const [session, setSession] = useState<Session>(initialSession);
  // ...refresh 는 그대로...
}
```

- [ ] **Step 4: root 에 loader 와 HydrateFallback 을 더한다**

`root.tsx` 에 추가한다.

```tsx
export async function clientLoader() {
  try {
    const response = await fetchSession();
    return {
      session: response
        ? ({ status: "authenticated", ...response } as Session)
        : ANONYMOUS,
    };
  } catch {
    // 서버에 닿지 못한 것과 비로그인을 구분하지 않는다. 어느 쪽이든 보호 화면은 못 연다.
    return { session: ANONYMOUS };
  }
}

/** ssr:false 에서 root clientLoader 가 도는 동안 보여줄 화면. */
export function HydrateFallback() {
  return <SessionPending />;
}

export default function Root() {
  const { session } = useLoaderData<typeof clientLoader>();
  return (
    <SessionProvider initialSession={session}>
      <Outlet />
    </SessionProvider>
  );
}
```

- [ ] **Step 5: layout 가드를 loader 로 옮긴다**

세 layout 이 같은 모양이다. `onboarding/layout.tsx` 예시:

```tsx
export async function clientLoader({ request }: { request: Request }) {
  const session = await requireSession();
  const guard = guardOnboarding(session, new URL(request.url).pathname);
  if (guard.allow !== true) {
    throw redirect(guard.redirectTo);
  }
  return null;
}

export default function OnboardingLayout() {
  return <AppShell navLabel="온보딩 단계" items={STEPS} />;
}
```

`requireSession()` 은 `app/lib/session.ts` 에 더한다 — root loader 가 이미 읽은 세션을 layout
loader 가 다시 읽지 않도록 모듈 수준에 캐시한다.

```ts
let cached: Session | null = null;

/** root clientLoader 가 채우고 하위 layout loader 가 읽는다. */
export function setSession(session: Session): void {
  cached = session;
}

export async function requireSession(): Promise<Session> {
  if (cached) return cached;
  const response = await fetchSession();
  cached = response
    ? { status: "authenticated", ...response }
    : ANONYMOUS;
  return cached;
}
```

root `clientLoader` 는 반환 전에 `setSession(session)` 을 부른다. `refresh` 도 성공 후
`setSession` 으로 캐시를 갱신한다. `guard.allow === "pending"` 은 loader 에서 나올 수 없다 —
세션이 이미 확정돼 있다.

- [ ] **Step 6: 라우트 테스트를 새 구조로 고친다**

`onboarding.test.tsx` 의 layout 렌더 테스트를 `createRoutesStub` 으로 바꾼다.

```tsx
import { createRoutesStub } from "react-router";

it("온보딩 완료 사용자가 소득 화면을 열면 대시보드로 보낸다", async () => {
  setSession({
    status: "authenticated",
    userId: "u-1",
    email: "a@b.kr",
    onboardingStep: "COMPLETED",
  });

  const Stub = createRoutesStub([
    {
      path: "/onboarding/income",
      // layout 모듈의 clientLoader 를 이름을 바꿔 가져온다:
      // import { clientLoader as onboardingLayoutLoader } from "./layout";
      loader: onboardingLayoutLoader,
      Component: () => <div>온보딩</div>,
    },
    { path: "/dashboard", Component: () => <div>대시보드</div> },
  ]);

  render(<Stub initialEntries={["/onboarding/income"]} />);

  expect(await screen.findByText("대시보드")).toBeInTheDocument();
});
```

`PlaceholderScreen` 의 화면 ID 문자열을 단언하던 개별 화면 테스트는 이 태스크에서 지우지
않는다 — Task 7~12 가 화면별로 교체한다.

- [ ] **Step 7: 통과를 확인한다**

Run: `cd frontend && pnpm test:unit && pnpm typecheck && pnpm lint`
Expected: PASS

- [ ] **Step 8: 커밋**

```bash
git add frontend/app
git commit -m "refactor(frontend): 세션 조회와 진입 가드를 라우터 loader 로 이관

렌더 시점 가드가 만들던 pending 분기를 없앤다. routeGuards 순수 함수는
그대로 쓰고 호출 위치만 layout loader 로 옮겼다."
```

---

## Task 5: shadcn/ui 배선

설계 §6. 별칭이 없어 shadcn 이 그냥은 안 붙는다.

**Files:**
- Modify: `frontend/tsconfig.json`, `frontend/vite.config.ts`, `frontend/app/styles/tokens.css`
- Create: `frontend/components.json`
- Create: `frontend/app/lib/utils.ts`, `frontend/app/components/ui/*`
- Test: `frontend/app/components/ui/button.test.tsx`

**Interfaces:**
- Produces: `@/` 가 `frontend/app/` 를 가리킨다
- Produces: `cn(...)` (`app/lib/utils.ts`) — `clsx` + `tailwind-merge`
- Produces: `Button`·`Input`·`Label`·`Select`·`Card`·`Badge`·`Table`·`Alert` 를
  `@/components/ui/*` 에서 가져온다

- [ ] **Step 1: 별칭을 넣는다**

`tsconfig.json` 의 `compilerOptions` 에 추가한다.

```json
    "baseUrl": ".",
    "paths": { "@/*": ["./app/*"] }
```

`vite.config.ts` 의 반환 객체에 추가한다.

```ts
    resolve: {
      alias: { "@": fileURLToPath(new URL("./app", import.meta.url)) },
    },
```

`vitest.config.ts` 에도 같은 `resolve.alias` 를 넣는다 — 없으면 테스트에서만 해석이 깨진다.

- [ ] **Step 2: 의존성을 설치한다**

```bash
cd frontend && pnpm add class-variance-authority clsx tailwind-merge lucide-react
```

- [ ] **Step 3: 토큰을 매핑한다**

`app/styles/tokens.css` 의 `@layer base` 안 `:root` 에 shadcn 이 기대하는 이름을 carix 변수로
정의한다. 팔레트를 두 벌 만들지 않는다.

```css
    --background: var(--carix-bg);
    --foreground: var(--carix-text);
    --card: var(--carix-surface);
    --card-foreground: var(--carix-text);
    --border: var(--carix-border);
    --input: var(--carix-border);
    --ring: var(--carix-info);
    --primary: var(--carix-info);
    --primary-foreground: #ffffff;
    --destructive: var(--carix-danger);
    --radius: 0.5rem;
```

`.dark` 블록과 `prefers-color-scheme` 블록은 carix 변수만 바꾸므로 추가 작업이 없다.

- [ ] **Step 4: `cn` 과 컴포넌트를 넣는다**

`app/lib/utils.ts`:

```ts
import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
```

`components.json` 을 만들고 shadcn CLI 로 `button input label select card badge table alert` 를
받는다. CLI 가 `app/components/ui/` 아래에 파일을 놓도록 `aliases` 를 맞춘다.

- [ ] **Step 5: 배선이 실제로 도는지 테스트로 못박는다**

```tsx
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Button } from "@/components/ui/button";

describe("shadcn 배선", () => {
  it("@/ 별칭으로 가져온 Button 이 렌더된다", () => {
    render(<Button>저장</Button>);
    expect(screen.getByRole("button", { name: "저장" })).toBeInTheDocument();
  });
});
```

- [ ] **Step 6: 통과를 확인한다**

Run: `cd frontend && pnpm test:unit app/components/ui && pnpm typecheck && pnpm build`
Expected: PASS. `pnpm build` 까지 도는 이유는 별칭이 vite 빌드에서도 풀려야 하기 때문이다

- [ ] **Step 7: 커밋**

```bash
git add frontend
git commit -m "chore(frontend): shadcn/ui 배선 및 @/ 경로 별칭 도입

shadcn 토큰을 carix 변수로 정의해 팔레트를 한 벌로 유지한다."
```

---

## Task 6: Zod 검증 스키마

설계 §5. 허용 범위의 정본은 [기능 명세 §3](../specs/functional-specification.md) 이고 서버가
그 범위를 대부분 검증하지 않으므로 화면이 유일한 방어선이다.

**Files:**
- Create: `frontend/app/lib/schemas/{profile,income,expense,scenario}.ts`
- Test: `frontend/app/lib/schemas/{profile,income,expense,scenario}.test.ts`

**Interfaces:**
- Produces: `profileSchema`, `incomeSchema`, `expenseSchema`, `scenarioSchema` (Zod 객체)
- Produces: `ProfileInput`, `IncomeInput`, `ExpenseInput`, `ScenarioInput` — 각 파일에서
  `export type X = z.infer<typeof xSchema>` 로 함께 내보낸다. 뒤 태스크의 API 모듈이 이 이름을 쓴다
- Produces: `JOB_FAMILIES`, `EMPLOYMENT_TYPES`, `COMPANY_SIZES`, `REGIONS`, `EXPENSE_CATEGORIES`
  — `{value, label}` 배열. 화면의 선택지가 여기서만 나온다
- Produces: `app/lib/schemas/options.ts` — `Option` 타입과 `values(options)` 헬퍼. 네 스키마
  파일이 함께 쓰므로 한곳에 둔다

- [ ] **Step 1: 설치**

```bash
cd frontend && pnpm add zod
```

- [ ] **Step 2: 프로필 스키마의 실패 테스트를 쓴다**

```ts
import { describe, expect, it } from "vitest";
import { JOB_FAMILIES, profileSchema } from "./profile";

describe("profileSchema", () => {
  const valid = {
    jobFamily: "DEVELOPER",
    yearsOfExperience: 5,
    region: "서울",
    employmentType: "FULL_TIME",
    companySize: "MEDIUM",
  };

  it("정상 입력을 통과시킨다", () => {
    expect(profileSchema.safeParse(valid).success).toBe(true);
  });

  it("직군은 집계 키 네 값만 받는다", () => {
    expect(JOB_FAMILIES.map((o) => o.value)).toEqual([
      "DEVELOPER",
      "DATA",
      "AI",
      "OTHER",
    ]);
    expect(
      profileSchema.safeParse({ ...valid, jobFamily: "BACKEND" }).success,
    ).toBe(false);
  });

  it("총 경력은 0~50년이다", () => {
    expect(
      profileSchema.safeParse({ ...valid, yearsOfExperience: -1 }).success,
    ).toBe(false);
    expect(
      profileSchema.safeParse({ ...valid, yearsOfExperience: 51 }).success,
    ).toBe(false);
  });

  it("회사 규모는 선택이다", () => {
    const { companySize: _drop, ...withoutSize } = valid;
    expect(profileSchema.safeParse(withoutSize).success).toBe(true);
  });
});
```

- [ ] **Step 3: 실패를 확인한다**

Run: `cd frontend && pnpm test:unit app/lib/schemas/profile.test.ts`
Expected: FAIL — 모듈 없음

- [ ] **Step 4: 프로필 스키마를 만든다**

값 어휘는 collector 의 분류기와 같아야 한다. 어긋나면 `market_aggregate` 조회가 조용히 비어
`표본 부족` 이 된다 (설계 §7.1).

먼저 `app/lib/schemas/options.ts` 를 만든다 — 네 스키마가 함께 쓴다.

```ts
export interface Option {
  value: string;
  label: string;
}

export const values = (options: Option[]) =>
  options.map((o) => o.value) as [string, ...string[]];
```

그다음 `profile.ts`:

```ts
import { z } from "zod";
import { values, type Option } from "./options";

/** collector 의 JobFamilyClassifier 가 만드는 값과 같아야 한다. */
export const JOB_FAMILIES: Option[] = [
  { value: "DEVELOPER", label: "개발" },
  { value: "DATA", label: "데이터" },
  { value: "AI", label: "AI/ML" },
  { value: "OTHER", label: "기타" },
];

export const EMPLOYMENT_TYPES: Option[] = [
  { value: "FULL_TIME", label: "정규직" },
  { value: "CONTRACT", label: "계약직" },
  { value: "FREELANCE", label: "프리랜서" },
  { value: "INTERN", label: "인턴" },
];

export const COMPANY_SIZES: Option[] = [
  { value: "LARGE", label: "대기업" },
  { value: "MEDIUM", label: "중견·중소" },
  { value: "STARTUP", label: "스타트업" },
];

/** collector 의 RegionRollup.SIDO 와 같은 어휘다. */
export const REGIONS: Option[] = [
  "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
  "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주",
].map((name) => ({ value: name, label: name }));

export const profileSchema = z.object({
  jobFamily: z.enum(values(JOB_FAMILIES)),
  yearsOfExperience: z.number().int().min(0).max(50),
  region: z.enum(values(REGIONS)),
  employmentType: z.enum(values(EMPLOYMENT_TYPES)),
  companySize: z.enum(values(COMPANY_SIZES)).optional(),
});

export type ProfileInput = z.infer<typeof profileSchema>;
```

- [ ] **Step 5: 통과를 확인한다**

Run: `cd frontend && pnpm test:unit app/lib/schemas/profile.test.ts`
Expected: PASS

- [ ] **Step 6: 소득 스키마를 같은 방식으로 만든다**

테스트가 먼저다. 경계값을 케이스로 쓴다 — 세전 연봉 1,000만 미만·30억 초과 거부, 월 실수령액
50만 미만·2억 초과 거부, 유효 시작일이 당월을 넘으면 거부.

```ts
export const incomeSchema = z.object({
  grossSalary: z.number().int().min(10_000_000).max(3_000_000_000),
  // 명세는 선택이지만 서버 IncomeRequest 가 @NotNull 이라 필수로 받는다 (설계 §8-a).
  // 0 을 대신 보내지 않는다 — 미입력과 구분되지 않아 PPI 가 세전 기반 추정으로 안 떨어진다.
  netSalary: z.number().int().min(500_000).max(200_000_000),
  effectiveDate: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/)
    .refine((value) => value <= new Date().toISOString().slice(0, 10), {
      message: "미래 날짜는 입력할 수 없습니다.",
    }),
});
```

- [ ] **Step 7: 지출 스키마를 만든다**

카테고리 정본은 `ExpenseCategoryEnum` 이다 (설계 §8-d).

```ts
export const EXPENSE_CATEGORIES: Option[] = [
  { value: "HOUSING", label: "주거·공공요금" },
  { value: "FOOD", label: "식비" },
  { value: "TRANSPORT", label: "교통" },
  { value: "MEDICAL", label: "의료" },
  { value: "EDUCATION", label: "교육·자기계발" },
  { value: "LEISURE", label: "여가·기타" },
];

/** 미입력 카테고리는 items 에서 빼서 보낸다 — 서버가 DEFAULT_CPI 로 보간한다. */
export const expenseSchema = z.object({
  items: z.array(
    z.object({
      category: z.enum(values(EXPENSE_CATEGORIES)),
      amount: z.number().int().min(0).max(50_000_000),
    }),
  ),
});
```

- [ ] **Step 8: 시나리오 스키마를 만든다**

인상률 범위는 기능 명세 §3(-50~+100)을 쓴다 — API 규격(-100~100)보다 좁아 양쪽을 통과한다
(설계 §8-f).

```ts
export const scenarioSchema = z.object({
  scenarioName: z.string().max(100).optional(),
  stayExpectedRaiseRate: z.number().min(-50).max(100),
  moveTargetGrossSalary: z.number().int().min(10_000_000).max(3_000_000_000),
  moveOneTimeCost: z.number().int().min(0).max(100_000_000),
  expectedUnemploymentMonths: z.number().int().min(0).max(6),
  isEligibleForUnemploymentBenefit: z.boolean(),
});
```

- [ ] **Step 9: 전체 통과를 확인한다**

Run: `cd frontend && pnpm test:unit app/lib/schemas && pnpm typecheck`
Expected: PASS

- [ ] **Step 10: 커밋**

```bash
git add frontend/app/lib/schemas frontend/package.json frontend/pnpm-lock.yaml
git commit -m "feat(frontend): 입력 검증 스키마와 선택지 어휘 추가

허용 범위 정본은 기능 명세 §3 이고 서버가 대부분 검증하지 않아
화면이 유일한 방어선이다. 직군·지역 값은 collector 분류기와 맞춘다."
```

---

## Task 7: `S-05` 프로필 화면

**Files:**
- Create: `frontend/app/lib/api/profile.ts`, `frontend/app/lib/onboardingApi.ts`
- Create: `frontend/app/components/onboarding/StepBadges.tsx`
- Modify: `frontend/app/routes/onboarding/profile.tsx`
- Test: `frontend/app/lib/api/profile.test.ts`, `frontend/app/routes/onboarding/profile.test.tsx`

**Interfaces:**
- Consumes: `coreGet`·`coreSend` (Task 3), `profileSchema`·선택지 상수 (Task 6)
- Produces: `getProfile(): Promise<ProfileResponse>`,
  `patchProfile(input: Partial<ProfileInput>): Promise<ProfileResponse>`
- Produces: `advanceOnboarding(step: OnboardingStep): Promise<{currentStep: string; completed: boolean}>`
- Produces: `StepBadges({ current }: { current: 1|2|3|4|5 })`
- Produces: `ProfileResponse` = `{jobFamily, yearsOfExperience, region, employmentType, companySize}`
  (모두 nullable)

- [ ] **Step 1: API 모듈의 실패 테스트를 쓴다**

```ts
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getProfile, patchProfile } from "./profile";

describe("profile API", () => {
  const originalFetch = globalThis.fetch;
  beforeEach(() => vi.restoreAllMocks());
  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("프로필을 조회한다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ success: true, data: { jobFamily: "DEVELOPER" } }),
    } as unknown as Response);

    await expect(getProfile()).resolves.toEqual({ jobFamily: "DEVELOPER" });
  });

  it("부분 필드만 보내 저장한다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ success: true, data: { jobFamily: "DATA" } }),
    } as unknown as Response);

    await patchProfile({ jobFamily: "DATA" });

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "/api/v1/profile",
      expect.objectContaining({
        method: "PATCH",
        body: JSON.stringify({ jobFamily: "DATA" }),
      }),
    );
  });
});
```

- [ ] **Step 2: 실패 확인 → 구현**

Run: `cd frontend && pnpm test:unit app/lib/api/profile.test.ts` → FAIL

```ts
import { coreGet, coreSend } from "../coreApi";
import type { ProfileInput } from "../schemas/profile";

export interface ProfileResponse {
  jobFamily: string | null;
  yearsOfExperience: number | null;
  region: string | null;
  employmentType: string | null;
  companySize: string | null;
}

export function getProfile() {
  return coreGet<ProfileResponse>("/v1/profile");
}

export function patchProfile(input: Partial<ProfileInput>) {
  return coreSend<ProfileResponse>("PATCH", "/v1/profile", input);
}
```

`onboardingApi.ts` 는 auth-service 경로라 봉투가 없다 — `coreApi` 를 쓰지 않는다.

```ts
import type { OnboardingStep } from "./session";

/** auth-service 는 ApiResult 로 감싸지 않는다 (설계 §4.3). */
export async function advanceOnboarding(step: OnboardingStep) {
  const response = await fetch("/api/v1/auth/onboarding", {
    method: "PATCH",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ currentStep: step }),
  });
  if (!response.ok) {
    throw new Error("온보딩 단계를 갱신하지 못했습니다.");
  }
  return (await response.json()) as { currentStep: string; completed: boolean };
}
```

Run: `cd frontend && pnpm test:unit app/lib/api/profile.test.ts` → PASS

- [ ] **Step 3: 화면의 실패 테스트를 쓴다**

`profile.test.tsx` 를 새로 만든다. 기존 `onboarding.test.tsx` 의 `S-05` PlaceholderScreen
단언은 지운다.

```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { describe, expect, it, vi } from "vitest";
import Profile, { clientLoader, clientAction } from "./profile";
import * as profileApi from "../../lib/api/profile";
import * as onboardingApi from "../../lib/onboardingApi";

describe("S-05 프로필 화면", () => {
  function renderScreen() {
    const Stub = createRoutesStub([
      { path: "/onboarding/profile", Component: Profile, loader: clientLoader, action: clientAction },
      { path: "/onboarding/income", Component: () => <div>소득 화면</div> },
    ]);
    return render(<Stub initialEntries={["/onboarding/profile"]} />);
  }

  it("저장된 프로필을 초기값으로 보여준다", async () => {
    vi.spyOn(profileApi, "getProfile").mockResolvedValue({
      jobFamily: "DATA",
      yearsOfExperience: 7,
      region: "부산",
      employmentType: "CONTRACT",
      companySize: null,
    });

    renderScreen();

    expect(await screen.findByLabelText("주 직무")).toHaveValue("DATA");
    expect(screen.getByLabelText("총 경력 (년)")).toHaveValue(7);
  });

  it("저장하면 프로필을 보내고 단계를 INCOME 으로 옮긴 뒤 소득 화면으로 간다", async () => {
    vi.spyOn(profileApi, "getProfile").mockResolvedValue({
      jobFamily: null, yearsOfExperience: null, region: null,
      employmentType: null, companySize: null,
    });
    const patch = vi.spyOn(profileApi, "patchProfile").mockResolvedValue({
      jobFamily: "DEVELOPER", yearsOfExperience: 5, region: "서울",
      employmentType: "FULL_TIME", companySize: null,
    });
    const advance = vi
      .spyOn(onboardingApi, "advanceOnboarding")
      .mockResolvedValue({ currentStep: "INCOME", completed: false });

    renderScreen();
    const user = userEvent.setup();

    await user.selectOptions(await screen.findByLabelText("주 직무"), "DEVELOPER");
    await user.type(screen.getByLabelText("총 경력 (년)"), "5");
    await user.selectOptions(screen.getByLabelText("희망 근무 지역"), "서울");
    await user.selectOptions(screen.getByLabelText("고용 형태"), "FULL_TIME");
    await user.click(screen.getByRole("button", { name: "다음" }));

    expect(await screen.findByText("소득 화면")).toBeInTheDocument();
    expect(patch).toHaveBeenCalled();
    expect(advance).toHaveBeenCalledWith("INCOME");
  });

  it("총 경력이 범위를 벗어나면 저장하지 않고 오류를 보여준다", async () => {
    vi.spyOn(profileApi, "getProfile").mockResolvedValue({
      jobFamily: null, yearsOfExperience: null, region: null,
      employmentType: null, companySize: null,
    });
    const patch = vi.spyOn(profileApi, "patchProfile");

    renderScreen();
    const user = userEvent.setup();

    await user.selectOptions(await screen.findByLabelText("주 직무"), "DEVELOPER");
    await user.type(screen.getByLabelText("총 경력 (년)"), "99");
    await user.selectOptions(screen.getByLabelText("희망 근무 지역"), "서울");
    await user.selectOptions(screen.getByLabelText("고용 형태"), "FULL_TIME");
    await user.click(screen.getByRole("button", { name: "다음" }));

    expect(await screen.findByText(/총 경력/)).toBeInTheDocument();
    expect(patch).not.toHaveBeenCalled();
  });
});
```

`userEvent` 가 없으면 설치한다: `pnpm add -D @testing-library/user-event`

- [ ] **Step 4: 실패를 확인한다**

Run: `cd frontend && pnpm test:unit app/routes/onboarding/profile.test.tsx`
Expected: FAIL — `clientLoader`·`clientAction` export 가 없다

- [ ] **Step 5: 화면을 구현한다**

`profile.tsx` 는 `clientLoader`(`getProfile`), `clientAction`(폼 → `profileSchema.safeParse` →
`patchProfile` → `advanceOnboarding("INCOME")` → `redirect("/onboarding/income")`), 기본
컴포넌트(`StepBadges current={1}` + 필드 다섯 + 「다음」·「저장 후 나가기」)를 export 한다.

검증 실패는 `{ errors: Record<string, string> }` 를 반환하고 화면이 `useActionData()` 로 읽어
필드 아래에 그린다. 저장 성공 피드백으로 「저장됨」 뱃지를 띄운다 — 자동 저장은 하지 않는다
(설계 §7.1).

- [ ] **Step 6: 통과를 확인한다**

Run: `cd frontend && pnpm test:unit app/routes/onboarding && pnpm typecheck && pnpm lint`
Expected: PASS

- [ ] **Step 7: 커밋**

```bash
git add frontend/app
git commit -m "feat(frontend): S-05 프로필 온보딩 화면 구현

선택지 값은 collector 분류기 어휘를 쓴다. 저장 후 온보딩 단계를
INCOME 으로 옮겨야 가드가 다음 화면을 연다."
```

---

## Task 8: `S-06` 소득 화면

**Files:**
- Create: `frontend/app/lib/api/income.ts`
- Modify: `frontend/app/routes/onboarding/income.tsx`
- Test: `frontend/app/lib/api/income.test.ts`, `frontend/app/routes/onboarding/income.test.tsx`

**Interfaces:**
- Consumes: `coreGet`·`coreSend`, `incomeSchema`, `advanceOnboarding`
- Produces: `IncomeResponse` = `{id: string; grossSalary: number; netSalary: number; effectiveDate: string}`
- Produces: `listIncome(): Promise<IncomeResponse[]>`,
  `createIncome(input): Promise<IncomeResponse>`,
  `updateIncome(id, input): Promise<IncomeResponse>`, `deleteIncome(id): Promise<null>`

- [ ] **Step 1: API 모듈 테스트를 쓰고 실패를 확인한다**

경로 네 개(`GET /v1/income`, `POST /v1/income`, `PUT /v1/income/{id}`, `DELETE /v1/income/{id}`)
가 각각 올바른 method·경로로 나가는지 `globalThis.fetch` mock 으로 단언한다. Task 7 Step 1 과
같은 모양이다.

Run: `cd frontend && pnpm test:unit app/lib/api/income.test.ts` → FAIL

- [ ] **Step 2: API 모듈을 구현하고 통과를 확인한다**

```ts
import { coreGet, coreSend } from "../coreApi";
import type { IncomeInput } from "../schemas/income";

export interface IncomeResponse {
  id: string;
  grossSalary: number;
  netSalary: number;
  effectiveDate: string;
}

export const listIncome = () => coreGet<IncomeResponse[]>("/v1/income");
export const createIncome = (input: IncomeInput) =>
  coreSend<IncomeResponse>("POST", "/v1/income", input);
export const updateIncome = (id: string, input: IncomeInput) =>
  coreSend<IncomeResponse>("PUT", `/v1/income/${id}`, input);
export const deleteIncome = (id: string) =>
  coreSend<null>("DELETE", `/v1/income/${id}`);
```

Run: `cd frontend && pnpm test:unit app/lib/api/income.test.ts` → PASS

- [ ] **Step 3: 화면 테스트를 쓴다**

케이스 넷:

1. 기존 이력이 표에 유효 시작일 내림차순으로 나온다
2. 이력을 추가하면 `createIncome` 이 불리고 표가 갱신된다
3. 같은 유효 시작일로 추가하면 서버가 `409` 를 주고, 화면이 「같은 유효 시작일이 이미
   있습니다.」를 필드 오류로 보여주며 입력값을 지우지 않는다
4. 「다음」을 누르면 `advanceOnboarding("EXPENSE")` 후 소비 화면으로 간다

409 는 `createIncome` mock 이 `{code:"DUPLICATE_EFFECTIVE_DATE", message:"같은 유효 시작일이
이미 있습니다.", status:409}` 를 reject 하도록 만든다.

- [ ] **Step 4: 실패를 확인한다**

Run: `cd frontend && pnpm test:unit app/routes/onboarding/income.test.tsx` → FAIL

- [ ] **Step 5: 화면을 구현한다**

`clientLoader` 는 `listIncome()`. 행 단위 추가·수정·삭제는 `useFetcher` 로 제출하고 성공 시
React Router 가 loader 를 다시 돌린다. 「다음」은 `clientAction` 으로 처리한다.

월 실수령액은 필수 입력으로 받는다 (설계 §7.2, §8-a). 라벨 옆에 「PPI 계산에 쓰입니다」를
적어 왜 필수인지 남긴다.

- [ ] **Step 6: 통과를 확인한다**

Run: `cd frontend && pnpm test:unit app/routes/onboarding && pnpm typecheck && pnpm lint`
Expected: PASS

- [ ] **Step 7: 커밋**

```bash
git add frontend/app
git commit -m "feat(frontend): S-06 소득 온보딩 화면 구현

유효 시작일 중복(409)은 해당 필드 오류로 착지시키고 입력을 보존한다."
```

---

## Task 9: `S-07` 소비 화면

**Files:**
- Create: `frontend/app/lib/api/expense.ts`, `frontend/app/components/expense/ExpenseDonut.tsx`
- Modify: `frontend/app/routes/onboarding/expense.tsx`
- Test: `frontend/app/lib/api/expense.test.ts`,
  `frontend/app/components/expense/ExpenseDonut.test.tsx`,
  `frontend/app/routes/onboarding/expense.test.tsx`

**Interfaces:**
- Consumes: `coreGet`·`coreSend`, `expenseSchema`·`EXPENSE_CATEGORIES`, `advanceOnboarding`
- Produces: `ExpenseHistoryResponse` =
  `{baseYearMonth: string; items: {category: string; amount: number; status: "USER_DEFINED"|"DEFAULT_CPI"}[]; weightStatus: string}`
- Produces: `listExpenses(): Promise<ExpenseHistoryResponse[]>`,
  `putExpenses(baseYearMonth: string, items): Promise<ExpenseHistoryResponse>`
- Produces: `ExpenseDonut({ items }: { items: {category: string; amount: number}[] })`

- [ ] **Step 1: 도넛의 실패 테스트를 쓴다**

차트 라이브러리를 쓰지 않으므로 비중 계산이 순수 함수여야 테스트된다.

```tsx
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ExpenseDonut, toShares } from "./ExpenseDonut";

describe("ExpenseDonut", () => {
  it("금액을 비중으로 바꾼다", () => {
    expect(
      toShares([
        { category: "HOUSING", amount: 750_000 },
        { category: "FOOD", amount: 250_000 },
      ]),
    ).toEqual([
      { category: "HOUSING", share: 75 },
      { category: "FOOD", share: 25 },
    ]);
  });

  it("합계가 0 이면 빈 배열을 돌려준다 — 0 으로 나누지 않는다", () => {
    expect(toShares([{ category: "HOUSING", amount: 0 }])).toEqual([]);
  });

  it("비중을 텍스트로도 읽을 수 있게 표시한다", () => {
    render(<ExpenseDonut items={[{ category: "HOUSING", amount: 100 }]} />);
    expect(screen.getByText("주거·공공요금 100%")).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 실패 확인 → 구현**

Run: `cd frontend && pnpm test:unit app/components/expense` → FAIL

`toShares` 는 합계 0 을 먼저 걸러 내고, 컴포넌트는 `conic-gradient` 로 원을 그리며 같은 값을
목록으로도 낸다 — 색만으로 정보를 전달하지 않는다.

Run: 같은 명령 → PASS

- [ ] **Step 3: API 모듈을 테스트와 함께 만든다**

`putExpenses("202609", items)` 가 `PUT /api/v1/expenses/202609` 로 `{items}` 를 보내는지
단언한다.

```ts
export const listExpenses = () => coreGet<ExpenseHistoryResponse[]>("/v1/expenses");
export const putExpenses = (
  baseYearMonth: string,
  items: { category: string; amount: number }[],
) =>
  coreSend<ExpenseHistoryResponse>("PUT", `/v1/expenses/${baseYearMonth}`, {
    items,
  });
```

- [ ] **Step 4: 화면 테스트를 쓴다**

케이스 넷:

1. 저장된 당월 지출이 카테고리별 초기값으로 들어간다
2. `status: "DEFAULT_CPI"` 인 카테고리에 「통계청 평균 기준」 뱃지가 붙는다
3. **비워 둔 카테고리는 `items` 에서 빠져 전송된다** — 0 을 보내지 않는다
4. 합계가 월 실수령액을 넘으면 경고가 뜨지만 저장은 진행된다

- [ ] **Step 5: 실패를 확인한다**

Run: `cd frontend && pnpm test:unit app/routes/onboarding/expense.test.tsx` → FAIL

- [ ] **Step 6: 화면을 구현한다**

`clientLoader` 는 `listExpenses()` 에서 당월(`YYYYMM`) 행을 고른다. 없으면 빈 입력이다.
`clientAction` 은 빈 칸을 제외한 `items` 만 모아 `putExpenses` 를 부르고
`advanceOnboarding("SKILLS")` 후 `/onboarding/skills` 로 보낸다.

> `S-08` 스킬 화면은 B 소관이라 이 계획 밖이다. 이 화면은 `SKILLS` 단계로 넘기기만 한다.

- [ ] **Step 7: 통과 확인 후 커밋**

Run: `cd frontend && pnpm test:unit && pnpm typecheck && pnpm lint`

```bash
git add frontend/app
git commit -m "feat(frontend): S-07 소비 온보딩 화면 구현

미입력 카테고리는 items 에서 빼 서버 보간(DEFAULT_CPI)에 맡기고
돌아온 상태를 그대로 뱃지로 그린다. 비중 도넛은 conic-gradient 다."
```

---

## Task 10: `S-09` 검토 화면

**Files:**
- Create: `frontend/app/lib/api/userSkills.ts`
- Modify: `frontend/app/routes/onboarding/review.tsx`
- Test: `frontend/app/routes/onboarding/review.test.tsx`

**Interfaces:**
- Consumes: `getProfile`, `listIncome`, `listExpenses`, `advanceOnboarding`
- Produces: `listUserSkills(): Promise<UserSkillResponse[]>` where `UserSkillResponse` =
  `{id: string; skillId: string; standardName: string; isCustom: boolean; monthsOfExperience: number; lastUsedDate: string}`

- [ ] **Step 1: 화면 테스트를 쓴다**

케이스 다섯:

1. 네 요약 카드(프로필·소득·소비·스킬)를 loader 가 모은 데이터로 그린다
2. `status: "DEFAULT_CPI"` 인 카테고리 이름이 「기본값으로 대체된 항목」에 나온다 — 문구를
   하드코딩하지 않는다
3. 필수 프로필 필드가 비어 있으면 제출 버튼 대신 해당 단계로 가는 안내를 보여준다
4. 제출하면 `advanceOnboarding("COMPLETED")` 후 `/dashboard` 로 간다
5. 소득 이력이 없으면 소득 카드가 「입력 필요」로 표시되고 제출이 막힌다

- [ ] **Step 2: 실패를 확인한다**

Run: `cd frontend && pnpm test:unit app/routes/onboarding/review.test.tsx` → FAIL

- [ ] **Step 3: 구현한다**

```ts
export async function clientLoader() {
  const [profile, income, expenses, skills] = await Promise.all([
    getProfile(),
    listIncome(),
    listExpenses(),
    listUserSkills(),
  ]);
  return { profile, income, expenses, skills };
}
```

완비 검증은 이 화면에 있다 ([API 규격 §3.2](../specs/api-specification.md) — 부분 저장을 허용한
대가). `jobFamily`·`yearsOfExperience`·`region`·`employmentType` 중 하나라도 비면 제출을 막고
해당 단계 링크를 띄운다.

「계산 제한 사항」 문구는 고정이다 — 세금·성과급·주식보상·퇴직금은 추정하지 않는다.

- [ ] **Step 4: 통과 확인 후 커밋**

Run: `cd frontend && pnpm test:unit && pnpm typecheck && pnpm lint`

```bash
git add frontend/app
git commit -m "feat(frontend): S-09 온보딩 검토 화면 구현

부분 저장을 허용한 대가로 필수 필드 완비 검증이 이 화면에 있다.
기본값 대체 항목은 지출 응답의 status 에서 만든다."
```

---

## Task 11: `S-10` 대시보드

**Files:**
- Create: `frontend/app/lib/api/dashboard.ts`, `frontend/app/components/dashboard/IndexCard.tsx`
- Modify: `frontend/app/routes/dashboard/index.tsx`
- Test: `frontend/app/lib/api/dashboard.test.ts`,
  `frontend/app/components/dashboard/IndexCard.test.tsx`,
  `frontend/app/routes/dashboard/dashboard.test.tsx` (교체)

**Interfaces:**
- Consumes: `coreGet`
- Produces: `DashboardSummary` — [API 규격 §5.1](../specs/api-specification.md) 응답 형태.
  `purchasingPowerIndex`·`marketValueIndex` 는
  `{value: number|null; changeFromLastMonth: number|null; status: string|null; indexStatus: IndexStatus; estimateLabel?: string}`,
  `decisionSignal` 은 `{code, label, rulesApplied: string[], rationale}`, `metadata` 는 §5.1 대로
- Produces: `IndexStatus` = `"OK"|"INSUFFICIENT_SAMPLE"|"UPDATE_DELAYED"|"INSUFFICIENT_HISTORY"|"NOT_AVAILABLE"`
- Produces: `IndexValue` = `{value: number|null; changeFromLastMonth: number|null; status: string|null; indexStatus: IndexStatus; estimateLabel?: string}`
- Produces: `fetchSummary(): Promise<{ok: true; data: DashboardSummary} | {ok: false; error: ApiError}>`
  — **던지지 않는다.** 열화가 정상 응답이다 (설계 §10)
- Produces: `IndexCard({ title, index }: { title: string; index: IndexValue })`

- [ ] **Step 1: `IndexCard` 의 실패 테스트를 쓴다**

```tsx
describe("IndexCard", () => {
  it("OK 면 값과 전월 대비 포인트를 보여준다", () => {
    render(
      <IndexCard
        title="구매력 지수"
        index={{ value: 94.2, changeFromLastMonth: -1.2, status: "DECREASED", indexStatus: "OK" }}
      />,
    );
    expect(screen.getByText("94.2")).toBeInTheDocument();
    expect(screen.getByText("▼ 1.2p")).toBeInTheDocument();
  });

  it("표본이 부족하면 값 대신 뱃지를 보여주고 0 이나 100 을 쓰지 않는다", () => {
    render(
      <IndexCard
        title="시장가치 지수"
        index={{ value: null, changeFromLastMonth: null, status: null, indexStatus: "INSUFFICIENT_SAMPLE" }}
      />,
    );
    expect(screen.getByText("표본 부족")).toBeInTheDocument();
    expect(screen.queryByText("0")).not.toBeInTheDocument();
    expect(screen.queryByText("100")).not.toBeInTheDocument();
  });

  it("업데이트 지연은 지연 뱃지로 표시한다", () => {
    render(
      <IndexCard
        title="구매력 지수"
        index={{ value: 91.0, changeFromLastMonth: null, status: null, indexStatus: "UPDATE_DELAYED" }}
      />,
    );
    expect(screen.getByText("업데이트 지연")).toBeInTheDocument();
  });

  it("계산 전 상태는 계산 대기로 표시한다", () => {
    render(
      <IndexCard
        title="시장가치 지수"
        index={{ value: null, changeFromLastMonth: null, status: null, indexStatus: "NOT_AVAILABLE" }}
      />,
    );
    expect(screen.getByText("계산 대기")).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 실패 확인 → `IndexCard` 구현 → 통과 확인**

Run: `cd frontend && pnpm test:unit app/components/dashboard`

- [ ] **Step 3: 화면 테스트를 쓴다**

`dashboard.test.tsx` 를 통째로 교체한다. 렌더 뼈대는 Task 7 과 같은 `createRoutesStub` 이고,
`fetchSummary` 를 `vi.spyOn` 으로 갈아 끼운다.

```tsx
const EMPTY_INDEX = {
  value: null,
  changeFromLastMonth: null,
  status: null,
  indexStatus: "NOT_AVAILABLE" as const,
};

function renderScreen() {
  const Stub = createRoutesStub([
    { path: "/dashboard", Component: DashboardHome, loader: clientLoader },
  ]);
  return render(<Stub initialEntries={["/dashboard"]} />);
}

it("지수 둘과 결정 신호 카드를 그린다", async () => {
  vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
    ok: true,
    data: {
      purchasingPowerIndex: {
        value: 94.2, changeFromLastMonth: -1.2, status: "DECREASED", indexStatus: "OK",
      },
      marketValueIndex: {
        value: 117.5, changeFromLastMonth: 3.8, status: "INCREASED", indexStatus: "OK",
      },
      decisionSignal: {
        code: "SEEK_JOB", label: "이직 탐색",
        rulesApplied: ["연봉 격차 15% 이상"],
        rationale: "공식 임금통계 중앙값보다 15% 이상 낮습니다.",
      },
      metadata: { baseDate: "2026-09-01", updateDelayed: false },
    },
  } as never);

  renderScreen();

  expect(await screen.findByText("94.2")).toBeInTheDocument();
  expect(screen.getByText("117.5")).toBeInTheDocument();
  expect(screen.getByText("이직 탐색")).toBeInTheDocument();
});

it("두 지수가 모두 계산 전이면 첫 계산 안내를 띄운다", async () => {
  vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
    ok: true,
    data: {
      purchasingPowerIndex: EMPTY_INDEX,
      marketValueIndex: EMPTY_INDEX,
      decisionSignal: null,
      metadata: { baseDate: "2026-09-01", updateDelayed: false },
    },
  } as never);

  renderScreen();

  expect(await screen.findByText("첫 지수를 계산하고 있습니다")).toBeInTheDocument();
});

it("조회에 실패하면 재시도 버튼을 보여주고 화면을 비우지 않는다", async () => {
  vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
    ok: false,
    error: { code: "UNKNOWN", message: "요청을 처리하지 못했습니다.", status: 502 },
  } as never);

  renderScreen();

  expect(await screen.findByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  expect(screen.getByText("요청을 처리하지 못했습니다.")).toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "핵심 지표" })).toBeInTheDocument();
});

it("근거를 펼치면 적용 규칙과 동료집단 조건이 보인다", async () => {
  vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
    ok: true,
    data: {
      purchasingPowerIndex: EMPTY_INDEX,
      marketValueIndex: { ...EMPTY_INDEX, value: 117.5, indexStatus: "OK" },
      decisionSignal: {
        code: "SEEK_JOB", label: "이직 탐색",
        rulesApplied: ["연봉 격차 15% 이상"],
        rationale: "공식 임금통계 중앙값보다 15% 이상 낮습니다.",
      },
      metadata: {
        baseDate: "2026-09-01",
        updateDelayed: false,
        peerGroup: { fallbackLevel: 3, appliedConditions: "서울 전체 / 경력 MID / 회사규모 무관" },
      },
    },
  } as never);

  renderScreen();
  await userEvent.setup().click(await screen.findByRole("button", { name: "근거 보기" }));

  expect(screen.getByText("연봉 격차 15% 이상")).toBeInTheDocument();
  expect(
    screen.getByText("서울 전체 / 경력 MID / 회사규모 무관"),
  ).toBeInTheDocument();
});
```

- [ ] **Step 4: 실패를 확인한다**

Run: `cd frontend && pnpm test:unit app/routes/dashboard/dashboard.test.tsx` → FAIL

- [ ] **Step 5: 구현한다**

```ts
export async function clientLoader() {
  return { summary: await fetchSummary() };
}
```

`fetchSummary` 는 `UnauthorizedError` 만 다시 던지고(가드가 처리한다) 나머지 오류는
`{ok:false, error}` 로 감싼다. 던지면 ErrorBoundary 가 마지막 성공 데이터를 잃는다 (설계 §10).

재시도는 `useRevalidator().revalidate()` 로 건다.

> **이 시점에는 정상 카드를 볼 수 없다.** A 의 PPI 구현이 들어오기 전까지
> `com.carix.index.stub` 이 `NOT_AVAILABLE` 을 돌려준다 (설계 §7.5). 위 테스트 2번이 지금
> 실제로 도는 경로이고, 1번은 mock 으로만 검증된다.

- [ ] **Step 6: 통과 확인 후 커밋**

Run: `cd frontend && pnpm test:unit && pnpm typecheck && pnpm lint`

```bash
git add frontend/app
git commit -m "feat(frontend): S-10 대시보드 3카드 구현

값이 없을 때 indexStatus 뱃지로 표시하고 0·100 으로 채우지 않는다.
loader 는 열화를 예외로 던지지 않아 마지막 성공 데이터가 남는다."
```

---

## Task 12: `S-15` 시뮬레이터

**Files:**
- Create: `frontend/app/lib/api/scenario.ts`
- Modify: `frontend/app/routes/dashboard/simulator.tsx`
- Test: `frontend/app/lib/api/scenario.test.ts`,
  `frontend/app/routes/dashboard/simulator.test.tsx`

**Interfaces:**
- Consumes: `coreGet`·`coreSend`, `scenarioSchema`
- Produces: `SimulationResult` =
  `{id, scenarioName, stayCompensation12m, moveCompensation12m, estimatedUnemploymentBenefit, unemploymentBenefitNote, difference, type, typeLabel, warningNote, feasibility}`
  where `feasibility` =
  `{status: "OK"|"INSUFFICIENT_SAMPLE"; sampleSize: number; minRequired: number|null; feasibilityScore: number|null; targetPercentile: number|null; difficultyGrade: string|null; feedbackMessage: string|null}`
- Produces: `getLatestScenario(): Promise<{inputs, result, calculatedAt} | null>` — 204 면 `null`
- Produces: `simulate(input: ScenarioInput): Promise<SimulationResult>`

- [ ] **Step 1: API 모듈을 테스트와 함께 만든다**

`getLatestScenario` 가 204 에서 `null` 을 돌려주는 케이스를 반드시 넣는다 —
`coreApi.unwrap` 이 이미 처리하지만 이 경로가 실제로 204 를 받는 유일한 곳이다.

```ts
export const getLatestScenario = () =>
  coreGet<{ inputs: ScenarioInput; result: SimulationResult; calculatedAt: string } | null>(
    "/v1/scenarios/latest",
  );
export const simulate = (input: ScenarioInput) =>
  coreSend<SimulationResult>("POST", "/v1/scenarios/simulate", input);
```

- [ ] **Step 2: 화면 테스트를 쓴다**

케이스 여섯:

1. 저장된 시나리오가 있으면 입력과 결과를 복원한다
2. `204`(저장분 없음)면 빈 폼을 보여주고 **기본값을 채우지 않는다**
3. 계산하면 `simulate` 가 불리고 결과 카드(`typeLabel`·`difference`·`warningNote`)가 나온다
4. `feasibility.status === "INSUFFICIENT_SAMPLE"` 이면 백분위·등급 자리에 「표본 부족」이 나오고
   숫자가 나오지 않는다
5. `isEligibleForUnemploymentBenefit` 이 거짓이면 「실업급여 미반영」이 나온다
6. **계산이 실패해도 이전 결과가 화면에 남고 오류가 함께 표시된다**

4번과 6번은 미묘하니 코드를 그대로 쓴다. 렌더 뼈대는 Task 11 과 같다.

```tsx
const OK_RESULT = {
  id: "s-1",
  scenarioName: "A사 오퍼",
  stayCompensation12m: 62_100_000,
  moveCompensation12m: 60_960_000,
  estimatedUnemploymentBenefit: 3_960_000,
  unemploymentBenefitNote: "가정 기반 추정 (기준 시점: 2026-01)",
  difference: -1_140_000,
  type: "ESTIMATED_OPPORTUNITY_COST",
  typeLabel: "추정 기회비용",
  warningNote: "세금, 성과급, 퇴직금 등은 포함하지 않습니다.",
  feasibility: {
    status: "OK", sampleSize: 42, minRequired: null,
    feasibilityScore: 31, targetPercentile: 69,
    difficultyGrade: "CHALLENGING", feedbackMessage: "상위 31% 수준입니다.",
  },
};

it("표본이 부족하면 백분위 대신 표본 부족을 보여준다", async () => {
  vi.spyOn(scenarioApi, "getLatestScenario").mockResolvedValue({
    inputs: SAVED_INPUTS,
    result: {
      ...OK_RESULT,
      feasibility: {
        status: "INSUFFICIENT_SAMPLE", sampleSize: 3, minRequired: 10,
        feasibilityScore: null, targetPercentile: null,
        difficultyGrade: null, feedbackMessage: null,
      },
    },
    calculatedAt: "2026-09-07T00:00:00Z",
  } as never);

  renderScreen();

  expect(await screen.findByText("표본 부족")).toBeInTheDocument();
  expect(screen.queryByText(/상위 \d+%/)).not.toBeInTheDocument();
});

it("계산이 실패해도 이전 결과가 남고 오류가 함께 나온다", async () => {
  vi.spyOn(scenarioApi, "getLatestScenario").mockResolvedValue({
    inputs: SAVED_INPUTS,
    result: OK_RESULT,
    calculatedAt: "2026-09-07T00:00:00Z",
  } as never);
  vi.spyOn(scenarioApi, "simulate").mockRejectedValue({
    code: "INVALID_INPUT", message: "입력값을 확인해 주세요.", status: 400,
  });

  renderScreen();
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "계산" }));

  expect(await screen.findByText("입력값을 확인해 주세요.")).toBeInTheDocument();
  expect(screen.getByText("추정 기회비용")).toBeInTheDocument();
});
```

`SAVED_INPUTS` 는 `scenarioSchema` 를 통과하는 값 하나를 같은 파일 위에 상수로 둔다.

- [ ] **Step 3: 실패를 확인한다**

Run: `cd frontend && pnpm test:unit app/routes/dashboard/simulator.test.tsx` → FAIL

- [ ] **Step 4: 구현한다**

`clientLoader` 는 `getLatestScenario()`. 제출은 `useFetcher` 로 한다 — loader 데이터가 남아
케이스 6이 별도 상태 관리 없이 성립한다 (설계 §7.6).

`currentGrossSalary` 는 입력으로 받지 않는다. 서버가 최신 소득 이력을 쓰고, 소득 이력이 없으면
`400` 이 오므로 그때는 「소득을 먼저 입력해 주세요」와 `S-06` 링크를 보여준다.

**금액을 `localStorage`·쿼리스트링·`console` 에 남기지 않는다** (전역 제약).

- [ ] **Step 5: 통과 확인 후 커밋**

Run: `cd frontend && pnpm test:unit && pnpm typecheck && pnpm lint`

```bash
git add frontend/app
git commit -m "feat(frontend): S-15 12개월 시뮬레이터 화면 구현

제출을 fetcher 로 보내 계산 실패 시 이전 결과가 남는다. 표본이
부족하면 백분위를 지어내지 않고 표본 부족으로 표시한다."
```

---

## Task 13: E2E 골격

설계 §11. 온보딩 → 대시보드 → 시뮬레이터 한 줄기의 뼈대를 세운다. 본 점검은 7주차다.

**Files:**
- Create: `frontend/e2e/onboarding-flow.spec.ts`

**Interfaces:**
- Consumes: Task 4 의 loader 가드, Task 7~12 의 화면
- Produces: `pnpm test:e2e` 에서 도는 진입 가드 검증. 인증이 필요한 본 흐름은 같은 파일에
  `test.describe` 로 두되 세션 쿠키를 만들 수 있을 때만 돌도록 환경변수로 가른다

- [ ] **Step 1: 가드 흐름의 실패 테스트를 쓴다**

세션 없이 도는 부분만 먼저다 — 백엔드 계정 없이 결정적으로 돈다.

```ts
import { expect, test } from "@playwright/test";

const PROTECTED = [
  "/onboarding/profile",
  "/onboarding/income",
  "/onboarding/expense",
  "/onboarding/review",
  "/dashboard",
  "/dashboard/simulator",
];

for (const path of PROTECTED) {
  test(`세션 없이 ${path} 를 열면 랜딩으로 돌아온다`, async ({ page }) => {
    await page.goto(path);
    await expect(page).toHaveURL("/");
  });
}

test.describe("로그인 후 온보딩 한 줄기", () => {
  test.skip(
    !process.env.CARIX_E2E_EMAIL,
    "CARIX_E2E_EMAIL·CARIX_E2E_PASSWORD 가 있을 때만 돈다 — 7주차 통합 점검용",
  );

  test("프로필부터 시뮬레이터까지 진행한다", async ({ page }) => {
    await page.goto("/login");
    await page.getByLabel("이메일").fill(process.env.CARIX_E2E_EMAIL!);
    await page.getByLabel("비밀번호").fill(process.env.CARIX_E2E_PASSWORD!);
    await page.getByRole("button", { name: "로그인" }).click();

    await expect(page).toHaveURL(/\/onboarding\/|\/dashboard/);
  });
});
```

- [ ] **Step 2: 실패를 확인한다**

Run: `cd frontend && pnpm test:e2e onboarding-flow`
Expected: 가드 테스트가 FAIL 하거나(리다이렉트가 안 걸리면) PASS 한다. 실패하면 Task 4 의
loader 가드가 빌드 산출물에서 안 도는 것이므로 거기부터 본다

- [ ] **Step 3: 필요하면 가드를 고치고 다시 돌린다**

`pnpm test:e2e` 는 `pnpm build && pnpm start` 로 실제 산출물을 띄운다. 단위 테스트가 통과해도
여기서 깨지면 `ssr: false` 의 `HydrateFallback` 배선을 먼저 확인한다.

- [ ] **Step 4: 커밋**

```bash
git add frontend/e2e
git commit -m "test(frontend): 온보딩 진입 가드 E2E 골격 추가

세션 없는 경로 차단은 상시 검증하고, 로그인 후 한 줄기는 계정
환경변수가 있을 때만 돈다. 본 점검은 7주차."
```

---

## Task 14: 문서 정정

설계 §2. 코드가 명세를 벗어난 자리를 문서에 반영한다. 고치지 않으면 다음 구현자가 무효가 된
명세를 그대로 본다.

**Files:**
- Modify: `docs/plans/carix-dev-plan.md`
- Modify: `docs/specs/frontend-design.md`
- Modify: `docs/specs/api-specification.md`

- [ ] **Step 1: 개발 계획서를 고친다**

- §1 C 행의 「`S-05`~`S-09` 온보딩 5화면 전체」를 `S-05`·`S-06`·`S-07`·`S-09` 로 고치고
  `S-08` 이 B 임을 적는다. 근거(14d→18d 산수)를 한 줄 남긴다
- §3 주차 표 C 3~4주차 칸에 같은 정정을 하고, `auth-service` 온보딩 단계 갱신을 항목으로
  추가한다 — `c3` 에 없던 작업이다
- 표기 규약대로 **지우지 않고 취소선 + 사유**로 남긴다

- [ ] **Step 2: 프런트엔드 설계를 고친다**

§기술 구성에서 TanStack Query·React Hook Form·MSW 를 걷고 Zod 를 넣는다. 서버 상태를 라우터
loader 로 다루는 이유(역할 중복)를 한 줄 적는다.

- [ ] **Step 3: API 규격을 고친다**

- §7.3 온보딩 상태: `core-api` 의 두 엔드포인트를 걷고 `PATCH /v1/auth/onboarding` 을 적는다.
  `GET /v1/onboarding` 은 세션 응답(`GET /v1/auth/session`)의 `onboardingStep` 이 대신한다
- §3.1 `region` 예시를 `SEOUL_GANGNAM` 에서 시/도 한글 어휘로 바꾼다
- §6.1 예상 인상률 범위가 기능 명세 §3 과 다른 것(-100~100 vs -50~+100)을 어느 쪽으로 맞출지
  적는다. 구현은 좁은 쪽을 건다

- [ ] **Step 4: 커밋**

```bash
git add docs
git commit -m "docs: c3 구현 결정에 따른 소관 문서 정정

S-08 소유, 온보딩 단계 갱신 경로 이동, 프런트엔드 스택 실제 구성,
region 값 어휘를 각 소관 문서에 반영한다."
```

---

## 남기는 것 — 다른 레인으로 넘길 항목

설계 §8 의 계약 불일치는 이 계획이 고치지 않는다. 화면은 현재 서버 동작에 맞춰 만들고, 아래는
담당에게 전달한다.

| # | 내용 | 담당 |
| :--- | :--- | :--- |
| a | `IncomeRequest.netSalary` 의 `@NotNull` 완화 | A |
| d | `ExpenseItem` Javadoc 의 카테고리 이름 정정 | A |
| e | `extractBroadRegion` 과 `RegionRollup` 의 광역 지역 표기 불일치 | B |
| g | 기능 명세 §3 허용 범위의 서버 측 검증 부재 | A·C |
