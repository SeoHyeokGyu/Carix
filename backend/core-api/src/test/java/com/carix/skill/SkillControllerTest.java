package com.carix.skill;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.common.GlobalExceptionHandler;
import com.carix.config.WebMvcConfig;
import com.carix.skill.dto.UserSkillCreateRequest;
import com.carix.skill.exception.DuplicateSkillException;
import com.carix.skill.exception.UserSkillNotFoundException;
import com.carix.skill.service.SkillService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SkillController.class)
@Import({GlobalExceptionHandler.class, WebMvcConfig.class})
class SkillControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SkillService skillService;

  @MockitoBean
  private com.carix.skill.service.SkillRecommendationService skillRecommendationService;

  @Test
  @DisplayName("POST /v1/user-skills - 중복 스킬 등록 시 409 Conflict를 반환한다")
  void createUserSkill_duplicate_returns409() throws Exception {
    given(skillService.createUserSkill(any(UUID.class), any(UserSkillCreateRequest.class)))
        .willThrow(new DuplicateSkillException("이미 등록된 스킬입니다: Spring Boot"));

    mockMvc
        .perform(
            post("/v1/user-skills")
                .header("X-User-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "standardName": "Spring Boot",
                      "monthsOfExperience": 24,
                      "lastUsedDate": "2026-07-01"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_SKILL"));
  }

  @Test
  @DisplayName("DELETE /v1/user-skills/{id} - 존재하지 않는 스킬 삭제 시 404 Not Found를 반환한다")
  void deleteUserSkill_notFound_returns404() throws Exception {
    UUID userSkillId = UUID.randomUUID();
    doThrow(new UserSkillNotFoundException("삭제할 보유 스킬을 찾을 수 없습니다."))
        .when(skillService)
        .deleteUserSkill(any(UUID.class), eq(userSkillId));

    mockMvc
        .perform(
            delete("/v1/user-skills/" + userSkillId)
                .header("X-User-Id", UUID.randomUUID().toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("GET /v1/user-skills - X-User-Id 가 없으면 401 을 반환한다")
  void getUserSkills_withoutUserHeader_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/v1/user-skills"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    verifyNoInteractions(skillService);
  }

  @Test
  @DisplayName(
      "POST /v1/user-skills - 30개 상한 초과 등록 시 400 Bad Request와 MAX_SKILL_LIMIT_EXCEEDED를 반환한다")
  void createUserSkill_exceedsLimit_returns400() throws Exception {
    given(skillService.createUserSkill(any(UUID.class), any(UserSkillCreateRequest.class)))
        .willThrow(
            new com.carix.skill.exception.MaxSkillLimitExceededException(
                "보유 스킬은 최대 30개까지만 등록할 수 있습니다."));

    mockMvc
        .perform(
            post("/v1/user-skills")
                .header("X-User-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "standardName": "Kubernetes",
                      "monthsOfExperience": 24,
                      "lastUsedDate": "2026-07-01"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MAX_SKILL_LIMIT_EXCEEDED"));
  }
}
