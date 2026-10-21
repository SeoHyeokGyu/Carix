package com.carix.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.common.usercontext.UserContextArgumentResolver;
import com.carix.profile.dto.ProfileResponse;
import com.carix.profile.service.ProfileService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProfileControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();

  private MockMvc mockMvc;
  private ProfileService profileService;

  @BeforeEach
  void setUp() {
    profileService = mock(ProfileService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ProfileController(profileService))
            .setCustomArgumentResolvers(new UserContextArgumentResolver())
            .build();
  }

  @Test
  @DisplayName("DELETE /v1/users - 정상 이메일 파라미터가 주어지면 탈퇴 서비스를 호출하고 200 반환")
  void deleteUser_정상() throws Exception {
    String email = "test@example.com";
    doNothing().when(profileService).deleteUser(eq(USER_ID), eq(email));

    mockMvc
        .perform(delete("/v1/users").header("X-User-Id", USER_ID.toString()).param("email", email))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("탈퇴 처리가 완료되었습니다. 모든 데이터가 삭제되었습니다."));

    verify(profileService).deleteUser(eq(USER_ID), eq(email));
  }

  @Test
  @DisplayName("DELETE /v1/users - 이메일 파라미터가 누락되면 400 Bad Request")
  void deleteUser_이메일_누락() throws Exception {
    mockMvc
        .perform(delete("/v1/users").header("X-User-Id", USER_ID.toString()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("PATCH /v1/profile - 입사일을 보내면 응답에 실린다")
  void 입사일을_패치하면_응답에_실린다() throws Exception {
    given(profileService.patch(eq(USER_ID), any()))
        .willReturn(new ProfileResponse(null, null, null, null, null, LocalDate.of(2022, 3, 2)));

    mockMvc
        .perform(
            patch("/v1/profile")
                .header("X-User-Id", USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hireDate\":\"2022-03-02\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.hireDate").value("2022-03-02"));
  }

  @Test
  @DisplayName("PATCH /v1/profile - 미래 입사일이면 400 Bad Request")
  void 미래_입사일은_400() throws Exception {
    String future = LocalDate.now().plusDays(1).toString();

    mockMvc
        .perform(
            patch("/v1/profile")
                .header("X-User-Id", USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hireDate\":\"" + future + "\"}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(profileService);
  }
}
