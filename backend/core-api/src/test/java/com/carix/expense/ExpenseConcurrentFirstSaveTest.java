package com.carix.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.carix.common.ErrorResponse;
import com.carix.common.GlobalExceptionHandler;
import com.carix.expense.dto.ExpenseHistoryResponse;
import com.carix.expense.dto.ExpenseUpdateRequest;
import com.carix.expense.service.AppliedExpenseService;
import com.carix.expense.service.ExpenseService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 그 달 소비 행이 없을 때 저장이 동시에 두 번 오면 둘 다 새 행을 만들다 뒤의 것이 {@code (user_id, base_year_month)} 유일 제약에 걸린다.
 * 운영에서 한 건이 500 으로 실패했고, 그 예외 로그에 user_id 가 그대로 찍혔다.
 */
class ExpenseConcurrentFirstSaveTest {

  private static final UUID USER_ID = UUID.randomUUID();

  @Test
  @DisplayName("첫 저장이 유일 제약에 걸리면 한 번 다시 저장해 먼저 들어간 행을 고친다")
  void 유일_제약에_걸리면_다시_저장한다() {
    ExpenseService service = mock(ExpenseService.class);
    ExpenseUpdateRequest request = new ExpenseUpdateRequest(List.of());
    ExpenseHistoryResponse saved = new ExpenseHistoryResponse("202611", List.of(), "USER_DEFINED");
    given(service.upsert(eq(USER_ID), eq("202611"), any()))
        .willThrow(new DataIntegrityViolationException("duplicate key"))
        .willReturn(saved);
    ExpenseController controller =
        new ExpenseController(service, mock(AppliedExpenseService.class));

    var response = controller.updateExpense(USER_ID, "202611", request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(service, times(2)).upsert(eq(USER_ID), eq("202611"), any());
  }

  @Test
  @DisplayName("그래도 남은 무결성 위반은 409 로 답하고 응답에 DB 메시지를 싣지 않는다")
  void 남은_무결성_위반은_409() {
    ResponseEntity<ErrorResponse> response =
        new GlobalExceptionHandler()
            .handleDataIntegrityViolation(
                new DataIntegrityViolationException(
                    "Key (user_id, base_year_month)=(" + USER_ID + ", 202611) already exists."));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(String.valueOf(response.getBody())).doesNotContain(USER_ID.toString());
  }
}
