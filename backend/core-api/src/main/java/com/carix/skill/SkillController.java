package com.carix.skill;

import com.carix.common.ApiResult;
import com.carix.common.ErrorResponse;
import com.carix.skill.dto.*;
import com.carix.skill.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Tag(name = "Skill Management", description = "표준 스킬 사전 검색 및 사용자의 보유 스킬 CRUD API")
public class SkillController {

  private static final UUID DEFAULT_FALLBACK_USER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000001");

  private final SkillService skillService;

  @Operation(summary = "표준 스킬 사전 검색", description = "입력한 검색어 기반으로 스킬 표준 리스트(자동완성용)를 반환합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "검색 성공",
      content =
          @Content(
              array = @ArraySchema(schema = @Schema(implementation = SkillSearchResponse.class))))
  @GetMapping("/skills/search")
  public ResponseEntity<ApiResult<List<SkillSearchResponse>>> searchSkills(
      @RequestParam("q") @NotBlank String query) {
    List<SkillSearchResponse> results = skillService.searchSkills(query);
    return ResponseEntity.ok(ApiResult.success(results));
  }

  @Operation(summary = "사용자 보유 스킬 목록 조회", description = "로그인된 사용자가 추가한 모든 보유 스킬 목록을 반환합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "조회 성공",
      content =
          @Content(
              array = @ArraySchema(schema = @Schema(implementation = UserSkillResponse.class))))
  @GetMapping("/user-skills")
  public ResponseEntity<ApiResult<List<UserSkillResponse>>> getUserSkills(
      @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false)
          UUID headerUserId) {
    UUID userId = resolveUserId(headerUserId);
    List<UserSkillResponse> userSkills = skillService.getUserSkills(userId);
    return ResponseEntity.ok(ApiResult.success(userSkills));
  }

  @Operation(
      summary = "사용자 보유 스킬 등록",
      description =
          "보유 스킬 리스트에 스킬을 새로 등록합니다. 스킬 사전에 존재하지 않는 스킬명일 경우 커스텀 스킬(`isCustom: true`)로 생성되며, 이 경우 직무 카테고리(`category`) 지정이 필수입니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "등록 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "잘못된 입력값",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "동일한 스킬 중복 등록 시도",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/user-skills")
  public ResponseEntity<ApiResult<UserSkillResponse>> createUserSkill(
      @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false)
          UUID headerUserId,
      @Valid @RequestBody UserSkillCreateRequest request) {
    UUID userId = resolveUserId(headerUserId);
    UserSkillResponse created = skillService.createUserSkill(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResult.success(created, "스킬이 등록되었습니다."));
  }

  @Operation(summary = "보유 스킬 정보 수정", description = "특정 ID에 매핑된 스킬의 실무 사용 월수 및 마지막 사용 시점을 수정합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "수정 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "잘못된 범위값",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "스킬을 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PutMapping("/user-skills/{id}")
  public ResponseEntity<ApiResult<UserSkillResponse>> updateUserSkill(
      @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false)
          UUID headerUserId,
      @PathVariable UUID id,
      @Valid @RequestBody UserSkillUpdateRequest request) {
    UUID userId = resolveUserId(headerUserId);
    UserSkillResponse updated = skillService.updateUserSkill(userId, id, request);
    return ResponseEntity.ok(ApiResult.success(updated, "스킬 정보가 수정되었습니다."));
  }

  @Operation(summary = "보유 스킬 정보 삭제", description = "보유 스킬 목록에서 특정 스킬을 삭제합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "삭제 성공"),
    @ApiResponse(
        responseCode = "404",
        description = "스킬을 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @DeleteMapping("/user-skills/{id}")
  public ResponseEntity<ApiResult<Void>> deleteUserSkill(
      @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false)
          UUID headerUserId,
      @PathVariable UUID id) {
    UUID userId = resolveUserId(headerUserId);
    skillService.deleteUserSkill(userId, id);
    return ResponseEntity.ok(ApiResult.success(null, "스킬이 삭제되었습니다."));
  }

  @Operation(
      summary = "동료집단 기반 추천 스킬 조회",
      description = "사용자의 동료집단 공고 수요 데이터를 기반으로 보유하고 있지 않은 인기 상위 스킬 목록을 추천합니다.")
  @ApiResponse(responseCode = "200", description = "조회 성공")
  @GetMapping("/skills/recommendations")
  public ResponseEntity<ApiResult<SkillRecommendationsResponse>> getSkillRecommendations(
      @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false)
          UUID headerUserId) {
    UUID userId = resolveUserId(headerUserId);
    // TODO: [동료집단 추천 엔진 연동]
    // 1. career_profile 도메인 완성 후 사용자의 직무(job_family), 연차(experience), 지역(region) 정보 조회
    // 2. 해당 동료집단(Peer Group) 공고들의 스킬 요구 빈도 및 트렌드(UP/DOWN/STABLE) 통계 집계
    // 3. 사용자가 이미 보유한 스킬을 제외한 상위 추천 스킬 목록 계산하여 반환하도록 연동 필요
    SkillRecommendationsResponse response = skillService.getSkillRecommendations(userId);
    return ResponseEntity.ok(ApiResult.success(response));
  }

  /**
   * 요청자 User ID 해석.
   *
   * <p>TODO: [인증 시스템 연동] 현재 core-api에 Spring Security / 세션 인증 필터가 연결되기 전이므로 게이트웨이 헤더(X-User-Id) 또는
   * 테스트용 Fallback ID를 사용 중입니다. 추후 auth-service 및 Gateway 세션 연동 완료 시 @AuthenticationPrincipal 또는 공통
   * UserContextResolver로 교체해야 합니다.
   */
  private UUID resolveUserId(UUID headerUserId) {
    return headerUserId != null ? headerUserId : DEFAULT_FALLBACK_USER_ID;
  }
}
