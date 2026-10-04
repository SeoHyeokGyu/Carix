package com.carix.skill.exception;

public class UserSkillNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public UserSkillNotFoundException(String message) {
    super(message);
  }
}
