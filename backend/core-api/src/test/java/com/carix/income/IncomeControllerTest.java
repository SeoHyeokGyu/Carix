package com.carix.income;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    mockMvc = MockMvcBuilders.standaloneSetup(new IncomeController(incomeService)).build();
  }

  @Test
  @DisplayName("GET /v1/income - 소득 이력 전체 조회 성공")
  void getIncomeHistory() throws Exception {
    UUID id = UUID.randomUUID();
    IncomeResponse res =
        new IncomeResponse(id, 60000000L, 4500000L, LocalDate.of(2026, 1, 1), null, null);
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
    IncomeResponse res = new IncomeResponse(id, 65000000L, 4800000L, date, null, null);
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
    IncomeResponse res = new IncomeResponse(id, 70000000L, 5000000L, date, null, null);
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
}
