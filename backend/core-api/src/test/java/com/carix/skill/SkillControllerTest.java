package com.carix.skill;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.common.GlobalExceptionHandler;
import com.carix.skill.dto.SkillSearchResponse;
import com.carix.skill.dto.UserSkillCreateRequest;
import com.carix.skill.dto.UserSkillResponse;
import com.carix.skill.dto.UserSkillUpdateRequest;
import com.carix.skill.exception.DuplicateSkillException;
import com.carix.skill.exception.UserSkillNotFoundException;
import com.carix.skill.service.SkillService;
import java.time.LocalDate;
import java.util.List;
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
@Import(GlobalExceptionHandler.class)
class SkillControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SkillService skillService;

  @Test
  @DisplayName("GET /v1/skills/search - 검색어에 따른 표준 스킬 목록을 반환한다")
  void searchSkills_returnsSkillList() throws Exception {
    UUID skillId = UUID.randomUUID();
    given(skillService.searchSkills("spring"))
        .willReturn(List.of(new SkillSearchResponse(skillId, "Spring Boot", false)));

    mockMvc
        .perform(get("/v1/skills/search").param("q", "spring"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].standardName").value("Spring Boot"))
        .andExpect(jsonPath("$.data[0].isCustom").value(false));
  }

  @Test
  @DisplayName("GET /v1/user-skills - 사용자의 보유 스킬 목록을 반환한다")
  void getUserSkills_returnsUserSkillList() throws Exception {
    UUID userSkillId = UUID.randomUUID();
    UUID skillId = UUID.randomUUID();
    given(skillService.getUserSkills(any(UUID.class)))
        .willReturn(
            List.of(
                new UserSkillResponse(
                    userSkillId, skillId, "Spring Boot", false, 24, LocalDate.of(2026, 7, 1))));

    mockMvc
        .perform(
            get("/v1/user-skills")
                .header("X-User-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].standardName").value("Spring Boot"))
        .andExpect(jsonPath("$.data[0].monthsOfExperience").value(24));
  }

  @Test
  @DisplayName("POST /v1/user-skills - 스킬 등록 성공 시 201 Created를 반환한다")
  void createUserSkill_success_returns201() throws Exception {
    UUID userSkillId = UUID.randomUUID();
    UUID skillId = UUID.randomUUID();
    given(skillService.createUserSkill(any(UUID.class), any(UserSkillCreateRequest.class)))
        .willReturn(
            new UserSkillResponse(
                userSkillId, skillId, "Spring Boot", false, 24, LocalDate.of(2026, 7, 1)));

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
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.standardName").value("Spring Boot"));
  }

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
  @DisplayName("PUT /v1/user-skills/{id} - 스킬 수정 성공 시 200 OK를 반환한다")
  void updateUserSkill_success_returns200() throws Exception {
    UUID userSkillId = UUID.randomUUID();
    UUID skillId = UUID.randomUUID();
    given(
            skillService.updateUserSkill(
                any(UUID.class), eq(userSkillId), any(UserSkillUpdateRequest.class)))
        .willReturn(
            new UserSkillResponse(
                userSkillId, skillId, "Spring Boot", false, 36, LocalDate.of(2026, 8, 1)));

    mockMvc
        .perform(
            put("/v1/user-skills/" + userSkillId)
                .header("X-User-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "monthsOfExperience": 36,
                      "lastUsedDate": "2026-08-01"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.monthsOfExperience").value(36));
  }

  @Test
  @DisplayName("DELETE /v1/user-skills/{id} - 스킬 삭제 성공 시 200 OK를 반환한다")
  void deleteUserSkill_success_returns200() throws Exception {
    UUID userSkillId = UUID.randomUUID();

    mockMvc
        .perform(
            delete("/v1/user-skills/" + userSkillId)
                .header("X-User-Id", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
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
}
