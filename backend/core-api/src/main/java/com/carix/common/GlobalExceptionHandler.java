package com.carix.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 요청 검증 실패를 규격의 공통 에러 포맷으로 변환한다.
 *
 * <p>기본 동작(Spring의 ProblemDetail)은 {@code docs/specs/api-specification.md} §1.1 과 형식이 다르다. 프런트엔드가 한
 * 가지 형태만 다루도록 여기서 맞춘다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
    Map<String, String> details = new LinkedHashMap<>();
    for (var error : e.getBindingResult().getFieldErrors()) {
      details.putIfAbsent(error.getField(), error.getDefaultMessage());
    }
    return ResponseEntity.badRequest()
        .body(ErrorResponse.of("INVALID_INPUT", "입력값 형식이 올바르지 않습니다.", details));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse.of("INVALID_INPUT", e.getMessage(), null));
  }

  @ExceptionHandler(com.carix.skill.exception.InvalidSkillCategoryException.class)
  public ResponseEntity<ErrorResponse> handleInvalidSkillCategory(
      com.carix.skill.exception.InvalidSkillCategoryException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse.of("INVALID_SKILL_CATEGORY", e.getMessage(), null));
  }

  @ExceptionHandler(com.carix.skill.exception.MaxSkillLimitExceededException.class)
  public ResponseEntity<ErrorResponse> handleMaxSkillLimitExceeded(
      com.carix.skill.exception.MaxSkillLimitExceededException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse.of("MAX_SKILL_LIMIT_EXCEEDED", e.getMessage(), null));
  }

  @ExceptionHandler(com.carix.expense.exception.InvalidExpenseCategoryException.class)
  public ResponseEntity<ErrorResponse> handleInvalidExpenseCategory(
      com.carix.expense.exception.InvalidExpenseCategoryException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse.of("INVALID_EXPENSE_CATEGORY", e.getMessage(), null));
  }

  @ExceptionHandler(com.carix.skill.exception.DuplicateSkillException.class)
  public ResponseEntity<ErrorResponse> handleDuplicateSkill(
      com.carix.skill.exception.DuplicateSkillException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse.of("DUPLICATE_SKILL", e.getMessage(), null));
  }

  @ExceptionHandler(com.carix.income.exception.DuplicateEffectiveDateException.class)
  public ResponseEntity<ErrorResponse> handleDuplicateEffectiveDate(
      com.carix.income.exception.DuplicateEffectiveDateException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse.of("DUPLICATE_EFFECTIVE_DATE", e.getMessage(), null));
  }

  @ExceptionHandler(com.carix.common.usercontext.MissingUserContextException.class)
  public ResponseEntity<ErrorResponse> handleMissingUserContext(
      com.carix.common.usercontext.MissingUserContextException e) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(ErrorResponse.of("UNAUTHORIZED", e.getMessage(), null));
  }

  @ExceptionHandler({
    com.carix.skill.exception.SkillNotFoundException.class,
    com.carix.skill.exception.UserSkillNotFoundException.class,
    com.carix.income.exception.IncomeNotFoundException.class
  })
  public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse.of("NOT_FOUND", e.getMessage(), null));
  }
}
