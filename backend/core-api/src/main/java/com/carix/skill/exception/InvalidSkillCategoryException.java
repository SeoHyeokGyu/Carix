package com.carix.skill.exception;

public class InvalidSkillCategoryException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidSkillCategoryException(String message) {
    super(message);
  }
}
