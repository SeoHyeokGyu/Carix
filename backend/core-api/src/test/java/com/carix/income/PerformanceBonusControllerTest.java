package com.carix.income;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.common.GlobalExceptionHandler;
import com.carix.income.exception.InvalidPaidMonthException;
import com.carix.income.exception.PerformanceBonusNotFoundException;
import com.carix.income.service.PerformanceBonusService;
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
  @DisplayName("DELETE - 남의 기록·없는 id 는 404")
  void 삭제_404() throws Exception {
    UUID id = UUID.randomUUID();
    org.mockito.Mockito.doThrow(new PerformanceBonusNotFoundException(id))
        .when(service)
        .delete(USER_ID, id);

    mockMvc
        .perform(delete("/v1/income/bonuses/" + id).header("X-User-Id", USER_ID.toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("성과급 기록을 찾을 수 없습니다: " + id));
  }
}
