package com.carix.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.common.GlobalExceptionHandler;
import com.carix.common.usercontext.UserContextArgumentResolver;
import com.carix.profile.exception.InvalidHireDateException;
import com.carix.profile.service.ProfileService;
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
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("PATCH /v1/profile - 미래 입사일이면 400 과 details.hireDate")
  void 미래_입사일은_400() throws Exception {
    given(profileService.patch(eq(USER_ID), any()))
        .willThrow(new InvalidHireDateException("입사일은 오늘 이후일 수 없습니다."));

    mockMvc
        .perform(
            patch("/v1/profile")
                .header("X-User-Id", USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hireDate\":\"2999-01-01\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.details.hireDate").value("입사일은 오늘 이후일 수 없습니다."));
  }
}
