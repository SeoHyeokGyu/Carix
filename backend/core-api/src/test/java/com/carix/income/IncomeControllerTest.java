package com.carix.income;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.common.GlobalExceptionHandler;
import com.carix.income.domain.IncomeChangeReasonEnum;
import com.carix.income.dto.BonusTermsRequest;
import com.carix.income.dto.IncomeRequest;
import com.carix.income.dto.IncomeResponse;
import com.carix.income.service.IncomeService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IncomeControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();

  private IncomeService incomeService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    incomeService = mock(IncomeService.class);
    // standaloneSetup 은 @RestControllerAdvice 를 자동으로 줍지 않는다 — 400/404/409 계약을
    // 컨트롤러 테스트에서 확인하려면 직접 붙여야 한다.
    mockMvc =
        MockMvcBuilders.standaloneSetup(new IncomeController(incomeService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("GET /v1/income - 소득 이력 전체 조회 성공")
  void getIncomeHistory() throws Exception {
    UUID id = UUID.randomUUID();
    IncomeResponse res =
        new IncomeResponse(
            id, 60000000L, 4500000L, LocalDate.of(2026, 1, 1), null, null, true, null);
    given(incomeService.getHistory(USER_ID)).willReturn(List.of(res));

    mockMvc
        .perform(get("/v1/income").header("X-User-Id", USER_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].grossSalary").value(60000000L));
  }

  @Test
  @DisplayName("POST /v1/income - 소득 정보 등록 성공 201 Created")
  void createIncome() throws Exception {
    UUID id = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 7, 1);
    IncomeResponse res = new IncomeResponse(id, 65000000L, 4800000L, date, null, null, true, null);
    given(incomeService.create(eq(USER_ID), any(IncomeRequest.class))).willReturn(res);

    mockMvc
        .perform(
            post("/v1/income")
                .header("X-User-Id", USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "grossSalary": 65000000,
                      "netSalary": 4800000,
                      "effectiveDate": "2026-07-01"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.grossSalary").value(65000000L));
  }

  @Test
  @DisplayName("PUT /v1/income/{id} - 소득 정보 수정 성공 200 OK")
  void updateIncome() throws Exception {
    UUID id = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 7, 1);
    IncomeResponse res = new IncomeResponse(id, 70000000L, 5000000L, date, null, null, true, null);
    given(incomeService.update(eq(USER_ID), eq(id), any(IncomeRequest.class))).willReturn(res);

    mockMvc
        .perform(
            put("/v1/income/" + id)
                .header("X-User-Id", USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "grossSalary": 70000000,
                      "netSalary": 5000000,
                      "effectiveDate": "2026-07-01"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.grossSalary").value(70000000L));
  }

  @Test
  @DisplayName("DELETE /v1/income/{id} - 소득 정보 삭제 성공 200 OK")
  void deleteIncome() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc
        .perform(delete("/v1/income/" + id).header("X-User-Id", USER_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(incomeService).delete(USER_ID, id);
  }

  @Test
  @DisplayName("POST /v1/income - changeReason 이 INITIAL 이면 400")
  void createIncome_rejectsInitial() throws Exception {
    given(incomeService.create(eq(USER_ID), any(IncomeRequest.class)))
        .willThrow(
            new IllegalArgumentException(
                "changeReason 에 INITIAL 을 지정할 수 없습니다. 최초 소득 행은 서버가 판정합니다."));

    mockMvc
        .perform(
            post("/v1/income")
                .header("X-User-Id", USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "grossSalary": 65000000,
                      "netSalary": 4800000,
                      "effectiveDate": "2026-07-01",
                      "changeReason": "INITIAL"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  @DisplayName("GET /v1/income - changeReason 과 selectedChangeReason 을 함께 싣는다")
  void getIncomeHistory_carriesBothReasons() throws Exception {
    IncomeResponse res =
        new IncomeResponse(
            UUID.randomUUID(),
            58000000L,
            3800000L,
            LocalDate.of(2024, 1, 1),
            IncomeChangeReasonEnum.INITIAL,
            IncomeChangeReasonEnum.JOB_CHANGE,
            true,
            null);
    given(incomeService.getHistory(USER_ID)).willReturn(List.of(res));

    mockMvc
        .perform(get("/v1/income").header("X-User-Id", USER_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].changeReason").value("INITIAL"))
        .andExpect(jsonPath("$.data[0].selectedChangeReason").value("JOB_CHANGE"));
  }

  @Test
  @DisplayName("POST /v1/income - enum 에 없는 changeReason 도 공통 오류 봉투로 400 을 낸다")
  void createIncome_rejectsUnknownChangeReason() throws Exception {
    // 잘못된 enum 값은 Bean Validation 이 아니라 Jackson 이 HttpMessageNotReadableException 으로
    // 던진다 — 전용 핸들러가 없으면 Spring 기본 본문으로 나가 공통 봉투를 벗어난다.
    mockMvc
        .perform(
            post("/v1/income")
                .header("X-User-Id", USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "grossSalary": 65000000,
                      "netSalary": 4800000,
                      "effectiveDate": "2026-07-01",
                      "changeReason": "FOO"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.message").value("요청 본문을 읽을 수 없습니다."));
  }

  @Test
  @DisplayName("POST /v1/income - 본문을 읽지 못해도 요청 값이 응답에 새지 않는다")
  void createIncome_unreadableBodyDoesNotEchoRequest() throws Exception {
    // 완료 기준 10번(민감 금액 미기록)의 응답 쪽 대응 — Jackson 예외 메시지를 그대로 실으면
    // 본문 조각과 함께 연봉이 따라 나간다.
    mockMvc
        .perform(
            post("/v1/income")
                .header("X-User-Id", USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "grossSalary": 123456789,
                      "netSalary": 4800000,
                      "effectiveDate": "2026-07-01",
                      "changeReason": "FOO"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("123456789"))))
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("FOO"))));
  }

  private static final String APPLY_FROM = "11111111-1111-1111-1111-111111111111";

  private org.springframework.test.web.servlet.ResultActions putBonusTerms(String json)
      throws Exception {
    return mockMvc.perform(
        put("/v1/income/bonus-terms")
            .header("X-User-Id", USER_ID.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  @Test
  @DisplayName("PUT /v1/income/bonus-terms - 따로 받음이면 200 과 갱신된 행 목록")
  void updateBonusTerms() throws Exception {
    IncomeResponse res =
        new IncomeResponse(
            UUID.fromString(APPLY_FROM),
            60000000L,
            4500000L,
            LocalDate.of(2026, 3, 1),
            null,
            null,
            false,
            6_000_000L);
    given(incomeService.updateBonusTerms(eq(USER_ID), any(BonusTermsRequest.class)))
        .willReturn(List.of(res));

    putBonusTerms(
            """
            {"applyFromIncomeId": "%s", "bonusIncluded": false, "fixedBonusAnnual": 6000000}
            """
                .formatted(APPLY_FROM))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].bonusIncluded").value(false))
        .andExpect(jsonPath("$.data[0].fixedBonusAnnual").value(6_000_000L));
  }

  @Test
  @DisplayName("PUT /v1/income/bonus-terms - 들어있음인데 고정상여가 있으면 fixedBonusAnnual 필드 400")
  void 들어있음이면_고정상여는_null로_저장한다() throws Exception {
    putBonusTerms(
            """
            {"applyFromIncomeId": "%s", "bonusIncluded": true, "fixedBonusAnnual": 3000000}
            """
                .formatted(APPLY_FROM))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.details.fixedBonusAnnual").exists());

    verify(incomeService, org.mockito.Mockito.never()).updateBonusTerms(any(), any());
  }

  @Test
  @DisplayName("PUT /v1/income/bonus-terms - 따로받음인데 금액이 0원이면 400")
  void 따로받음인데_금액이_0원이면_400() throws Exception {
    putBonusTerms(
            """
            {"applyFromIncomeId": "%s", "bonusIncluded": false, "fixedBonusAnnual": 0}
            """
                .formatted(APPLY_FROM))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.fixedBonusAnnual").exists());

    verify(incomeService, org.mockito.Mockito.never()).updateBonusTerms(any(), any());
  }

  @Test
  @DisplayName("PUT /v1/income/bonus-terms - 따로받음인데 금액이 없으면 400")
  void 따로받음인데_금액이_없으면_400() throws Exception {
    putBonusTerms(
            """
            {"applyFromIncomeId": "%s", "bonusIncluded": false}
            """
                .formatted(APPLY_FROM))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.fixedBonusAnnual").exists());

    verify(incomeService, org.mockito.Mockito.never()).updateBonusTerms(any(), any());
  }

  @Test
  @DisplayName("PUT /v1/income/bonus-terms - 필수 필드 누락·음수 금액은 400")
  void updateBonusTerms_rejectsInvalid() throws Exception {
    putBonusTerms("{\"bonusIncluded\": true}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.applyFromIncomeId").exists());
    putBonusTerms(
            """
            {"applyFromIncomeId": "%s", "bonusIncluded": false, "fixedBonusAnnual": -1}
            """
                .formatted(APPLY_FROM))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.fixedBonusAnnual").exists());
  }
}
