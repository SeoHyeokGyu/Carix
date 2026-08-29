package com.carix.expense.exception;

public class InvalidExpenseCategoryException extends RuntimeException {
  public InvalidExpenseCategoryException(String category) {
    super("유효하지 않은 소비 카테고리입니다: " + category);
  }
}
