package com.carix.datasource;

import java.util.concurrent.Callable;

/**
 * 읽기 전용 커넥션 풀 라우팅 컨텍스트.
 *
 * <p>가상 스레드(Virtual Threads) 등 트랜잭션 경계 바깥에서 비동기 병렬 I/O를 수행할 때 명시적으로 읽기 전용 커넥션 풀(CarixReadOnlyPool)을
 * 사용하도록 지정한다.
 */
public final class ReadOnlyRoutingContext {

  private static final ThreadLocal<Boolean> READ_ONLY = new ThreadLocal<>();

  private ReadOnlyRoutingContext() {}

  public static void setReadOnly(boolean readOnly) {
    if (readOnly) {
      READ_ONLY.set(Boolean.TRUE);
    } else {
      READ_ONLY.remove();
    }
  }

  public static boolean isReadOnly() {
    return Boolean.TRUE.equals(READ_ONLY.get());
  }

  public static void clear() {
    READ_ONLY.remove();
  }

  public static <T> Callable<T> withReadOnlyCallable(Callable<T> callable) {
    return () -> {
      setReadOnly(true);
      try {
        return callable.call();
      } finally {
        clear();
      }
    };
  }

  public static Runnable withReadOnly(Runnable runnable) {
    return () -> {
      setReadOnly(true);
      try {
        runnable.run();
      } finally {
        clear();
      }
    };
  }
}
