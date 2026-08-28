package com.carix.authservice.magiclink;

import jakarta.validation.constraints.NotBlank;

/**
 * 매직링크 검증 요청. 토큰만 받는다.
 *
 * <p>이메일을 함께 받지 않는 이유는 링크를 요청한 기기와 여는 기기가 다를 수 있기 때문이다 — 데스크톱에서 요청하고 휴대폰에서 링크를 여는 것이 매직링크의 표준 사용
 * 패턴이고, 그 화면은 이메일을 알 방법이 없다. 링크에 이메일을 실으면 URL·리퍼러·서버 로그에 개인정보가 남는다.
 *
 * <p>보안상으로도 더해 주는 것이 없다. 토큰은 256비트 난수이고 1회만 쓰이며 사용자 매핑을 서버가 갖는다 — 토큰을 가진 쪽만 검증에 성공한다.
 */
public record VerifyRequest(@NotBlank String token) {}
