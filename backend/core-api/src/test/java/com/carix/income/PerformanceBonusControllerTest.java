package com.carix.income;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.common.GlobalExceptionHandler;
import com.carix.income.dto.PerformanceBonusRequest;
import com.carix.income.dto.PerformanceBonusResponse;
import com.carix.income.exception.IncomeNotFoundException;
import com.carix.income.exception.InvalidPaidMonthException;
import com.carix.income.service.PerformanceBonusService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PerformanceBonusControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();

  private PerformanceBonusService service;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = mock(PerformanceBonusService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new PerformanceBonusController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("GET /v1/income/bonuses - 목록 조회")
  void 목록_조회() throws Exception {
    given(service.list(USER_ID))
        .willReturn(List.of(new PerformanceBonusResponse(UUID.randomUUID(), "202607", 3_000_000L)));

    mockMvc
        .perform(get("/v1/income/bonuses").header("X-User-Id", USER_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].paidMonth").value("202607"))
        .andExpect(jsonPath("$.data[0].amount").value(3_000_000L));
  }

  @Test
  @DisplayName("POST /v1/income/bonuses - 201")
  void 등록_201() throws Exception {
    given(service.create(USER_ID, new PerformanceBonusRequest("202607", 3_000_000L)))
        .willReturn(new PerformanceBonusResponse(UUID.randomUUID(), "202607", 3_000_000L));

    mockMvc
        .perform(
            post("/v1/income/bonuses")
                .header("X-User-Id", USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paidMonth\":\"202607\",\"amount\":3000000}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.paidMonth").value("202607"));
  }

  @Test
  @DisplayName("지급월 형식이 아니면 400 과 필드별 details")
  void 지급월_형식_오류는_400() throws Exception {
    mockMvc
        .perform(
            post("/v1/income/bonuses")
                .header("X-User-Id", USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paidMonth\":\"2026-07\",\"amount\":1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.details.paidMonth").exists());
  }

  @Test
  @DisplayName("금액이 0 이하이면 400")
  void 금액_0은_400() throws Exception {
    mockMvc
        .perform(
            post("/v1/income/bonuses")
                .header("X-User-Id", USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paidMonth\":\"202607\",\"amount\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.amount").exists());
  }

  @Test
  @DisplayName("미래 지급월은 400 이고 details.paidMonth 에 사유")
  void 미래_지급월은_400() throws Exception {
    given(service.create(any(), any())).willThrow(new InvalidPaidMonthException("미래"));

    mockMvc
        .perform(
            post("/v1/income/bonuses")
                .header("X-User-Id", USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paidMonth\":\"209912\",\"amount\":1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.details.paidMonth").value("미래"));
  }

  @Test
  @DisplayName("DELETE - 204")
  void 삭제_204() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc
        .perform(delete("/v1/income/bonuses/" + id).header("X-User-Id", USER_ID.toString()))
        .andExpect(status().isNoContent());
    verify(service).delete(USER_ID, id);
  }

  @Test
  @DisplayName("DELETE - 남의 기록·없는 id 는 404")
  void 삭제_404() throws Exception {
    UUID id = UUID.randomUUID();
    org.mockito.Mockito.doThrow(new IncomeNotFoundException(id)).when(service).delete(USER_ID, id);

    mockMvc
        .perform(delete("/v1/income/bonuses/" + id).header("X-User-Id", USER_ID.toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }
}
