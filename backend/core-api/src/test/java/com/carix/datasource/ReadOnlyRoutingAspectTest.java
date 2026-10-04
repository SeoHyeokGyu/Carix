package com.carix.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReadOnlyRoutingAspectTest {

  private final ReadOnlyRoutingAspect aspect = new ReadOnlyRoutingAspect();

  @AfterEach
  void tearDown() {
    ReadOnlyRoutingContext.clear();
  }

  @Test
  @DisplayName("메서드 실행 중 ReadOnlyRoutingContext가 true로 활성화되고 종료 후 복원된다")
  void activatesAndRestoresReadOnlyContext() throws Throwable {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    given(joinPoint.proceed())
        .willAnswer(
            invocation -> {
              assertThat(ReadOnlyRoutingContext.isReadOnly()).isTrue();
              return "success";
            });

    assertThat(ReadOnlyRoutingContext.isReadOnly()).isFalse();
    Object result = aspect.routeToReadOnly(joinPoint);
    assertThat(result).isEqualTo("success");
    assertThat(ReadOnlyRoutingContext.isReadOnly()).isFalse();
  }

  @Test
  @DisplayName("RuntimeException 발생 시 예외가 전파되고 컨텍스트가 안전하게 복원된다")
  void handlesRuntimeExceptionAndRestoresContext() throws Throwable {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    given(joinPoint.proceed())
        .willAnswer(
            invocation -> {
              assertThat(ReadOnlyRoutingContext.isReadOnly()).isTrue();
              throw new IllegalStateException("db read error");
            });

    assertThatThrownBy(() -> aspect.routeToReadOnly(joinPoint))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("db read error");

    assertThat(ReadOnlyRoutingContext.isReadOnly()).isFalse();
  }

  @Test
  @DisplayName("Checked Exception 발생 시 그대로 전파되고 컨텍스트가 복원된다")
  void handlesCheckedExceptionAndRestoresContext() throws Throwable {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    given(joinPoint.proceed())
        .willAnswer(
            invocation -> {
              assertThat(ReadOnlyRoutingContext.isReadOnly()).isTrue();
              throw new Exception("checked failure");
            });

    assertThatThrownBy(() -> aspect.routeToReadOnly(joinPoint))
        .isInstanceOf(Exception.class)
        .hasMessage("checked failure");

    assertThat(ReadOnlyRoutingContext.isReadOnly()).isFalse();
  }

  @Test
  @DisplayName("Error 발생 시 그대로 전파되고 컨텍스트가 복원된다")
  void handlesErrorAndRestoresContext() throws Throwable {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    given(joinPoint.proceed())
        .willAnswer(
            invocation -> {
              assertThat(ReadOnlyRoutingContext.isReadOnly()).isTrue();
              throw new AssertionError("assertion failed");
            });

    assertThatThrownBy(() -> aspect.routeToReadOnly(joinPoint))
        .isInstanceOf(AssertionError.class)
        .hasMessage("assertion failed");

    assertThat(ReadOnlyRoutingContext.isReadOnly()).isFalse();
  }

  @Test
  @DisplayName("기타 Throwable 발생 시 RuntimeException으로 래핑 전파되고 컨텍스트가 복원된다")
  void handlesOtherThrowableAndRestoresContext() throws Throwable {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    given(joinPoint.proceed())
        .willAnswer(
            invocation -> {
              assertThat(ReadOnlyRoutingContext.isReadOnly()).isTrue();
              throw new Throwable("generic throwable");
            });

    assertThatThrownBy(() -> aspect.routeToReadOnly(joinPoint))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(Throwable.class)
        .hasRootCauseMessage("generic throwable");

    assertThat(ReadOnlyRoutingContext.isReadOnly()).isFalse();
  }
}
