package com.carix.common.usercontext;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 요청자의 사용자 ID를 주입한다. 값의 출처는 게이트웨이가 세션을 검증한 뒤 넣는 {@code X-User-Id} 헤더다.
 *
 * <p>{@link UserContextArgumentResolver} 가 처리하며, 헤더가 없거나 UUID 형식이 아니면 401 이다. 즉 이 애너테이션이 붙은 파라미터는
 * 항상 인증된 사용자를 가리킨다 — 핸들러 쪽에 null 검사나 대체값이 필요 없다.
 *
 * <p>대상 타입은 {@link java.util.UUID} 다.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {}
