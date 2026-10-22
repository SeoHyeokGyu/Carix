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
}
