package com.carix.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReadOnlyRoutingContextTest {

  @AfterEach
  void tearDown() {
    ReadOnlyRoutingContext.clear();
  }

  @Test
  @DisplayName("기본 상태에서는 isReadOnly가 false를 반환한다")
  void defaultIsFalse() {
    assertThat(ReadOnlyRoutingContext.isReadOnly()).isFalse();
  }

  @Test
  @DisplayName("setReadOnly(true) 호출 시 true, setReadOnly(false) 호출 시 false를 반환한다")
  void setReadOnlyExplicitly() {
    ReadOnlyRoutingContext.setReadOnly(true);
    assertThat(ReadOnlyRoutingContext.isReadOnly()).isTrue();

    ReadOnlyRoutingContext.setReadOnly(false);
    assertThat(ReadOnlyRoutingContext.isReadOnly()).isFalse();
  }

  @Test
  @DisplayName("clear 호출 시 컨텍스트가 초기화되어 isReadOnly가 false를 반환한다")
  void clearResetsContext() {
    ReadOnlyRoutingContext.setReadOnly(true);
    assertThat(ReadOnlyRoutingContext.isReadOnly()).isTrue();

    ReadOnlyRoutingContext.clear();
    assertThat(ReadOnlyRoutingContext.isReadOnly()).isFalse();
  }

  @Test
  @DisplayName("withReadOnlyCallable 실행 중에는 true이고, 종료 후 원복된다")
  void withReadOnlyCallableNormal() throws Exception {
    Callable<Boolean> task =
        ReadOnlyRoutingContext.withReadOnlyCallable(ReadOnlyRoutingContext::isReadOnly);
    Boolean result = task.call();

    assertThat(result).isTrue();
    assertThat(ReadOnlyRoutingContext.isReadOnly()).isFalse();
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
  @DisplayName("withReadOnly(Runnable) 실행 중에는 true이고, 종료 후 원복된다")
  void withReadOnlyRunnableNormal() {
    AtomicBoolean innerReadOnly = new AtomicBoolean(false);
    Runnable task =
        ReadOnlyRoutingContext.withReadOnly(
            () -> innerReadOnly.set(ReadOnlyRoutingContext.isReadOnly()));

    task.run();

    assertThat(innerReadOnly.get()).isTrue();
    assertThat(ReadOnlyRoutingContext.isReadOnly()).isFalse();
  }

  @Test
  @DisplayName("withReadOnly(Runnable) 실행 중 예외가 발생해도 컨텍스트가 반드시 clear된다")
  void withReadOnlyRunnableExceptionCleanup() {
    Runnable task =
        ReadOnlyRoutingContext.withReadOnly(
            () -> {
              assertThat(ReadOnlyRoutingContext.isReadOnly()).isTrue();
              throw new IllegalStateException("Fault");
            });

    assertThatThrownBy(task::run).isInstanceOf(IllegalStateException.class).hasMessage("Fault");

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
