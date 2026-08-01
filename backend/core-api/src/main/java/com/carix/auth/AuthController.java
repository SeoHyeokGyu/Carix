package com.carix.auth;

import com.carix.auth.dto.LoginResponse;
import com.carix.auth.dto.MagicLinkRequest;
import com.carix.auth.dto.MagicLinkResponse;
import com.carix.auth.dto.VerifyRequest;
import com.carix.common.ApiResult;
import com.carix.common.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Authentication", description = "인증 및 세션 관리 API")
public class AuthController {

  @Operation(summary = "매직링크 발송 요청", description = "이메일로 일회성 로그인 인증 링크를 발송합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "발송 성공"),
      @ApiResponse(responseCode = "400", description = "잘못된 입력", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/magic-link")
  public ResponseEntity<ApiResult<MagicLinkResponse>> requestMagicLink(
      @Valid @RequestBody MagicLinkRequest request) {
    return ResponseEntity.ok(ApiResult.success(
        new MagicLinkResponse("인증 링크가 발송되었습니다.", Instant.now()),
        "인증 링크가 발송되었습니다."
    ));
  }

  @Operation(summary = "매직링크 검증 및 로그인", description = "매직링크 토큰을 검증하여 세션을 생성하고 로그인 처리합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "로그인 성공"),
      @ApiResponse(responseCode = "400", description = "검증 실패 또는 토큰 만료", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/verify")
  public ResponseEntity<ApiResult<LoginResponse>> verifyMagicLink(
      @Valid @RequestBody VerifyRequest request) {
    return ResponseEntity.ok(ApiResult.success(
        new LoginResponse(
            UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
            request.email(),
            false
        ),
        "로그인에 성공했습니다."
    ));
  }

  @Operation(summary = "로그아웃", description = "현재 세션을 폐기합니다.")
  @ApiResponse(responseCode = "200", description = "로그아웃 성공")
  @PostMapping("/logout")
  public ResponseEntity<ApiResult<Void>> logout() {
    return ResponseEntity.ok(ApiResult.success(null, "로그아웃되었습니다."));
  }

  @Operation(summary = "전체 로그아웃", description = "해당 사용자의 모든 세션을 폐기합니다.")
  @ApiResponse(responseCode = "200", description = "로그아웃 성공")
  @PostMapping("/logout-all")
  public ResponseEntity<ApiResult<Void>> logoutAll() {
    return ResponseEntity.ok(ApiResult.success(null, "모든 기기에서 로그아웃되었습니다."));
  }
}
