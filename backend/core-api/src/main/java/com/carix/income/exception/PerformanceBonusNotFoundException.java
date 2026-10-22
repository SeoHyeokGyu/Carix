package com.carix.income.exception;

import java.util.UUID;

public class PerformanceBonusNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public PerformanceBonusNotFoundException(UUID id) {
    super("성과급 기록을 찾을 수 없습니다: " + id);
  }
}
