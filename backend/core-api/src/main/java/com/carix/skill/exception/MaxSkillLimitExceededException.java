package com.carix.skill.exception;

public class MaxSkillLimitExceededException extends RuntimeException {

  public MaxSkillLimitExceededException(String message) {
    super(message);
  }
}
