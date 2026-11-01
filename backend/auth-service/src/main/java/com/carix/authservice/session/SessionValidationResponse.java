package com.carix.authservice.session;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * 세션 조회 응답. 게이트웨이 세션 검증 필터와 프런트엔드가 함께 쓴다.
 *
 * <p>게이트웨이는 {@code userId} 만 읽고 나머지는 무시한다 — 필드를 더해도 그쪽은 깨지지 않는다. 프런트엔드는 {@code onboardingStep} 으로
 * 진입 가드를 판정한다.
 */
@Schema(description = "세션 조회 결과")
public record SessionValidationResponse(
    UUID userId,
    String email,
    @Schema(example = "PROFILE", description = "온보딩 진행 단계") String onboardingStep,
    @Schema(description = "게스트 여부. 게스트면 email 은 null") boolean guest) {}
