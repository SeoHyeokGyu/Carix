package com.carix.income.exception;

import java.time.LocalDate;

public class DuplicateEffectiveDateException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public DuplicateEffectiveDateException(LocalDate effectiveDate) {
    super("이미 등록된 유효 시작일입니다: " + effectiveDate);
  }
}
