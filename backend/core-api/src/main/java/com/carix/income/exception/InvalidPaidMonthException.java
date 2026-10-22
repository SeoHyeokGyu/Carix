package com.carix.income.exception;

public class InvalidPaidMonthException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidPaidMonthException(String message) {
    super(message);
  }
}
