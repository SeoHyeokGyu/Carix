package com.carix.skill;

import com.carix.common.ApiResult;
import com.carix.common.ErrorResponse;
import com.carix.skill.dto.SkillSearchResponse;
import com.carix.skill.dto.UserSkillCreateRequest;
import com.carix.skill.dto.UserSkillResponse;
import com.carix.skill.dto.UserSkillUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@Tag(name = "Skill Management", description = "표준 스킬 사전 검색 및 사용자의 보유 스킬 CRUD API")
public class SkillController {

  @Operation(summary = "표준 스킬 사전 검색", description = "입력한 검색어 기반으로 스킬 표준 리스트(자동완성용)를 반환합니다.")
  @ApiResponse(responseCode = "200", description = "검색 성공", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SkillSearchResponse.class))))
  @GetMapping("/skills/search")
  public ResponseEntity<ApiResult<List<SkillSearchResponse>>> searchSkills(
      @RequestParam("q") @NotBlank String query
  ) {
    return ResponseEntity.ok(ApiResult.success(List.of(
        new SkillSearchResponse(UUID.randomUUID(), "Spring Boot", false),
        new SkillSearchResponse(UUID.randomUUID(), "Spring Batch", false)
    )));
  }

  @Operation(summary = "사용자 보유 스킬 목록 조회", description = "로그인된 사용자가 추가한 모든 보유 스킬 목록을 반환합니다.")
  @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserSkillResponse.class))))
  @GetMapping("/user-skills")
  public ResponseEntity<ApiResult<List<UserSkillResponse>>> getUserSkills() {
    return ResponseEntity.ok(ApiResult.success(List.of(
        new UserSkillResponse(
            UUID.fromString("b812d8a4-0cfb-4a5f-9dfb-12d88c91350b"),
            "Spring Boot",
            false,
            24,
            LocalDate.of(2026, 7, 1)
        ),
        new UserSkillResponse(
            UUID.fromString("c91b5c4-2dfb-402a-bf39-44ef8c91361d"),
            "TypeScript",
            false,
            12,
            LocalDate.of(2026, 6, 15)
        )
    )));
  }

  @Operation(summary = "사용자 보유 스킬 등록", description = "보유 스킬 리스트에 스킬을 새로 등록합니다. 스킬 사전에 존재하지 않는 스킬명일 경우 커스텀 스킬(`isCustom: true`)로 생성되며, 이 경우 직무 카테고리(`category`) 지정이 필수입니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "등록 성공"),
      @ApiResponse(responseCode = "400", description = "잘못된 입력값", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(responseCode = "409", description = "동일한 스킬 중복 등록 시도", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/user-skills")
  public ResponseEntity<ApiResult<UserSkillResponse>> createUserSkill(
      @Valid @RequestBody UserSkillCreateRequest request) {
    ApiResult<UserSkillResponse> response = ApiResult.success(new UserSkillResponse(
        UUID.randomUUID(),
        request.standardName(),
        request.category() != null,
        request.monthsOfExperience(),
        request.lastUsedDate()
    ), "스킬이 등록되었습니다.");
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Operation(summary = "보유 스킬 정보 수정", description = "특정 ID에 매핑된 스킬의 실무 사용 월수 및 마지막 사용 시점을 수정합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "수정 성공"),
      @ApiResponse(responseCode = "400", description = "잘못된 범위값", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PutMapping("/user-skills/{id}")
  public ResponseEntity<ApiResult<UserSkillResponse>> updateUserSkill(
      @PathVariable UUID id,
      @Valid @RequestBody UserSkillUpdateRequest request
  ) {
    return ResponseEntity.ok(ApiResult.success(new UserSkillResponse(
        id,
        "Spring Boot",
        false,
        request.monthsOfExperience(),
        request.lastUsedDate()
    ), "스킬 정보가 수정되었습니다."));
  }

  @Operation(summary = "보유 스킬 정보 삭제", description = "보유 스킬 목록에서 특정 스킬을 삭제합니다.")
  @ApiResponse(responseCode = "200", description = "삭제 성공")
  @DeleteMapping("/user-skills/{id}")
  public ResponseEntity<ApiResult<Void>> deleteUserSkill(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResult.success(null, "스킬이 삭제되었습니다."));
  }
}
