package com.carix.profile.exception;

public class InvalidHireDateException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidHireDateException(String message) {
    super(message);
  }
}
