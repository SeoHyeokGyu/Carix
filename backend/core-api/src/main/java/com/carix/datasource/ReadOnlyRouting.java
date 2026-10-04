package com.carix.datasource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 대상 메서드 또는 클래스 내 모든 메서드의 DB 조회가 읽기 전용 커넥션 풀({@code CarixReadOnlyPool})로 라우팅되도록 보장하는 애너테이션.
 *
 * <p>{@link ReadOnlyRoutingAspect}에 의해 가로채져 {@link ReadOnlyRoutingContext}를 활성화하며, {@code
 * Propagation.NOT_SUPPORTED} 등으로 트랜잭션이 중단된 순수 조회 로직이나 비동기 작업에서 쓰기 풀 커넥션이 소모되는 것을 원천 방지한다.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ReadOnlyRouting {}
