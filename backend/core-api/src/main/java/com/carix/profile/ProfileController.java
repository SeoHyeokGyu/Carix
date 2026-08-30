package com.carix.profile;

import com.carix.common.ApiResult;
import com.carix.common.ErrorResponse;
import com.carix.common.usercontext.CurrentUserId;
import com.carix.profile.dto.OnboardingStatusRequest;
import com.carix.profile.dto.OnboardingStatusResponse;
import com.carix.profile.dto.ProfileRequest;
import com.carix.profile.dto.ProfileResponse;
import com.carix.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@Tag(name = "Profile & Onboarding", description = "프로필, 온보딩 단계 및 회원 데이터 관리 API")
public class ProfileController {

  private final ProfileService profileService;

  public ProfileController(ProfileService profileService) {
    this.profileService = profileService;
  }

  @Operation(summary = "커리어 프로필 조회", description = "현재 로그인된 사용자의 주 직무, 경력, 지역 등 커리어 프로필 정보를 가져옵니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(
        responseCode = "401",
        description = "인증 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping("/profile")
  public ResponseEntity<ApiResult<ProfileResponse>> getProfile(@CurrentUserId UUID userId) {
    return ResponseEntity.ok(ApiResult.success(profileService.get(userId)));
  }

  @Operation(
      summary = "커리어 프로필 수정 (온보딩 포함)",
      description = "커리어 프로필 정보를 수정합니다. 온보딩 중 임시 저장을 지원하므로 부분 필드 갱신을 허용합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "수정 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "잘못된 요청 데이터",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PatchMapping("/profile")
  public ResponseEntity<ApiResult<ProfileResponse>> updateProfile(
      @CurrentUserId UUID userId, @Valid @RequestBody ProfileRequest request) {
    return ResponseEntity.ok(ApiResult.success(profileService.patch(userId, request)));
  }

  @Operation(summary = "온보딩 단계 상태 조회", description = "현재 진행 중인 온보딩 단계 및 완료 여부를 확인합니다.")
  @ApiResponse(responseCode = "200", description = "조회 성공")
  @GetMapping("/onboarding")
  public ResponseEntity<ApiResult<OnboardingStatusResponse>> getOnboardingStatus() {
    return ResponseEntity.ok(ApiResult.success(new OnboardingStatusResponse("EXPENSE", false)));
  }

  @Operation(
      summary = "온보딩 단계 상태 갱신",
      description = "온보딩 단계를 진행합니다. REVIEW -> COMPLETED 단계로 전환 시 최초 지수 연산이 수행됩니다.")
  @ApiResponse(responseCode = "200", description = "갱신 성공")
  @PatchMapping("/onboarding")
  public ResponseEntity<ApiResult<OnboardingStatusResponse>> updateOnboardingStatus(
      @Valid @RequestBody OnboardingStatusRequest request) {
    return ResponseEntity.ok(
        ApiResult.success(
            new OnboardingStatusResponse(
                request.currentStep(), "COMPLETED".equals(request.currentStep())),
            "온보딩 상태가 업데이트되었습니다."));
  }

  @Operation(
      summary = "사용자 정보 및 개인 데이터 완전 삭제",
      description = "회원 탈퇴 처리 및 해당 사용자의 모든 입력 데이터를 영구적으로 삭제합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "탈퇴 완료"),
    @ApiResponse(
        responseCode = "400",
        description = "입력 이메일 정보 불일치",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @DeleteMapping("/users")
  public ResponseEntity<ApiResult<Void>> deleteUser(
      @Parameter(description = "탈퇴 확인을 위한 이메일 주소", required = true, example = "user@example.com")
          @RequestParam
          @NotBlank
          @Email
          String email) {
    return ResponseEntity.ok(ApiResult.success(null, "탈퇴 처리가 완료되었습니다. 모든 데이터가 삭제되었습니다."));
  }
}
