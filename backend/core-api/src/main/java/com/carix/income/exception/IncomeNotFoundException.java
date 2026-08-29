package com.carix.income.exception;

import java.util.UUID;

public class IncomeNotFoundException extends RuntimeException {
  public IncomeNotFoundException(UUID id) {
    super("소득 정보를 찾을 수 없습니다: " + id);
  }
}
