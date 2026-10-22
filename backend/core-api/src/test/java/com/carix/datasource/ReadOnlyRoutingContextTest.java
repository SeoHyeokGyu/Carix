package com.carix.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReadOnlyRoutingContextTest {

  @AfterEach
  void tearDown() {
    ReadOnlyRoutingContext.clear();
  }

  @Test
  @DisplayName("withReadOnlyCallable 실행 중 예외가 발생해도 컨텍스트가 반드시 clear된다")
  void withReadOnlyCallableExceptionCleanup() {
    Callable<String> task =
        ReadOnlyRoutingContext.withReadOnlyCallable(
            () -> {
              assertThat(ReadOnlyRoutingContext.isReadOnly()).isTrue();
              throw new RuntimeException("DB error");
            });

    assertThatThrownBy(task::call).isInstanceOf(RuntimeException.class).hasMessage("DB error");

    assertThat(ReadOnlyRoutingContext.isReadOnly()).isFalse();
  }

  @Test
  @DisplayName("가상 스레드에서도 독립적으로 격리되어 동작한다")
  void virtualThreadIsolation() throws Exception {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<Boolean> future =
          executor.submit(
              ReadOnlyRoutingContext.withReadOnlyCallable(ReadOnlyRoutingContext::isReadOnly));
      assertThat(future.get()).isTrue();
    }
    assertThat(ReadOnlyRoutingContext.isReadOnly()).isFalse();
  }
}
