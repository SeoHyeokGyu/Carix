package com.carix.skill.exception;

public class DuplicateSkillException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public DuplicateSkillException(String message) {
    super(message);
  }
}
