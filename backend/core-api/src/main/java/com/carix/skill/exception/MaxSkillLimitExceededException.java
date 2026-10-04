package com.carix.skill.exception;

public class MaxSkillLimitExceededException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public MaxSkillLimitExceededException(String message) {
    super(message);
  }
}
