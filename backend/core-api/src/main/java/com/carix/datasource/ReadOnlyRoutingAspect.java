package com.carix.datasource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * {@link ReadOnlyRouting} 애너테이션이 선언된 메서드 또는 클래스의 실행을 {@link
 * ReadOnlyRoutingContext#withReadOnlyCallable}로 감싸 읽기 전용 커넥션 풀을 사용하도록 라우팅한다.
 *
 * <p>트랜잭션 애너테이션({@code @Transactional})보다 먼저, 또는 트랜잭션이 없는 비동기/NOT_SUPPORTED 구간에서 안전하게 읽기 컨텍스트가
 * 주입되도록 높은 우선순위({@code HIGHEST_PRECEDENCE + 100})를 부여한다.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class ReadOnlyRoutingAspect {

  @Around("@annotation(ReadOnlyRouting) || @within(ReadOnlyRouting)")
  public Object routeToReadOnly(ProceedingJoinPoint joinPoint) throws Throwable {
    return ReadOnlyRoutingContext.withReadOnlyCallable(
            () -> {
              try {
                return joinPoint.proceed();
              } catch (Throwable t) {
                if (t instanceof RuntimeException re) {
                  throw re;
                }
                if (t instanceof Error err) {
                  throw err;
                }
                if (t instanceof Exception ex) {
                  throw ex;
                }
                throw new RuntimeException(t);
              }
            })
        .call();
  }
}
