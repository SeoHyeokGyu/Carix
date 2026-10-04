package com.carix.income.exception;

import java.util.UUID;

public class IncomeNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public IncomeNotFoundException(UUID id) {
    super("소득 정보를 찾을 수 없습니다: " + id);
  }
}
